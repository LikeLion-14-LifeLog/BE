package com.lifelog.global.infra.openai;

import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Responses API 호출과 구조화된 텍스트 역직렬화만 담당한다. 도메인 의미 검증은 호출자가 수행한다. */
@Slf4j
@Component
public class OpenApiUtil {
    private static final long MAX_RETRY_DELAY_MILLIS = 60_000;
    private static final Set<String> QUOTA_ERROR_CODES = Set.of("insufficient_quota", "billing_hard_limit_reached",
            "credit_balance_exhausted", "organization_spend_limit_exceeded", "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded");
    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String apiKey;
    private final String model;
    private final int maxAttempts;
    private final long retryDelayMillis;
    private final Clock clock;
    private final RetrySleeper sleeper;

    @Autowired
    public OpenApiUtil(RestClient.Builder builder, JsonMapper jsonMapper,
                       @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
                       @Value("${openai.api-key:}") String apiKey,
                       @Value("${openai.model:gpt-4.1-mini}") String model,
                       @Value("${openai.connect-timeout:5s}") Duration connectTimeout,
                       @Value("${openai.read-timeout:60s}") Duration readTimeout,
                       @Value("${openai.max-attempts:3}") int maxAttempts) {
        this(configureClient(builder, baseUrl, connectTimeout, readTimeout), jsonMapper, apiKey, model, maxAttempts, 2000);
    }

    OpenApiUtil(RestClient restClient, JsonMapper jsonMapper, String apiKey,
                String model, int maxAttempts, long retryDelayMillis) {
        this(restClient, jsonMapper, apiKey, model, maxAttempts, retryDelayMillis, Clock.systemUTC(), Thread::sleep);
    }

    /** 재시도 대기와 시계를 주입해 실제 네트워크·대기 없이 HTTP 오류 정책을 검증한다. */
    OpenApiUtil(RestClient restClient, JsonMapper jsonMapper, String apiKey,
                String model, int maxAttempts, long retryDelayMillis, Clock clock, RetrySleeper sleeper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxAttempts = Math.clamp(maxAttempts, 1, 3);
        this.retryDelayMillis = Math.clamp(retryDelayMillis, 0, MAX_RETRY_DELAY_MILLIS);
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * 개발자 지시와 사용자 데이터를 분리하여 전송하고 엄격한 JSON 스키마 응답을 읽는다.
     * @param prompt 프롬프트와 응답 스키마
     * @param responseType 응답 DTO 타입
     * @return 완료된 응답의 역직렬화 결과
     */
    public <T> T request(OpenApiPrompt prompt, Class<T> responseType) {
        log.info("[OpenApiUtil] 구조화 응답 요청 | request() - START | schema: {}, model: {}", prompt.schemaName(), model);
        if (apiKey == null || apiKey.isBlank()) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_KEY_NOT_CONFIGURED);
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "store", false,
                "max_output_tokens", 6000,
                "input", List.of(
                        Map.of("role", "developer", "content", prompt.developerInstructions()),
                        Map.of("role", "user", "content", prompt.inputJson())),
                "text", Map.of("format", Map.of("type", "json_schema", "name", prompt.schemaName(),
                        "strict", true, "schema", prompt.responseSchema())));
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            checkInterrupted();
            try {
                JsonNode response = restClient.post().uri("/responses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
                T result = parseResponse(response, responseType);
                log.info("[OpenApiUtil] 구조화 응답 요청 | request() - END | schema: {}", prompt.schemaName());
                return result;
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                HttpFailure failure = readHttpFailure(exception);
                log.warn("[OpenApiUtil] HTTP 호출 실패 | status: {}, attempt: {}, model: {}, requestId: {}, errorCode: {}, errorType: {}, param: {}, message: {}",
                        status, attempt, model, failure.requestId(), failure.code(), failure.type(), failure.param(), failure.message());
                /* (1) 잔액·결제·사용 한도 오류는 재시도로 해결되지 않으므로 즉시 종료한다. */
                if (QUOTA_ERROR_CODES.contains(failure.code()) || "insufficient_quota".equals(failure.type())) {
                    throw httpFailure(OpenApiErrorCode.OPEN_API_QUOTA_EXCEEDED, failure);
                }
                /* (2) 일시적 제한과 서버 오류만 Retry-After 또는 지수 백오프로 제한 횟수 안에서 재시도한다. */
                if ((status == 429 || exception.getStatusCode().is5xxServerError()) && attempt < maxAttempts) {
                    if (awaitRetry(attempt, failure.retryAfterMillis())) continue;
                }
                throw httpFailure(status == 429 ? OpenApiErrorCode.OPEN_API_RATE_LIMITED : OpenApiErrorCode.OPEN_API_REQUEST_FAILED, failure);
            } catch (ResourceAccessException exception) {
                checkInterrupted();
                throw new CustomException(isTimeout(exception)
                        ? OpenApiErrorCode.OPEN_API_TIMEOUT : OpenApiErrorCode.OPEN_API_REQUEST_FAILED);
            } catch (RestClientException exception) {
                throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
            }
        }
        throw new CustomException(OpenApiErrorCode.OPEN_API_REQUEST_FAILED);
    }

    /** HTTP 연결과 응답 시간 제한을 설정한다. */
    private static RestClient configureClient(RestClient.Builder builder, String baseUrl,
                                              Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** 거절·미완료·빈 응답을 제외하고 output 배열의 텍스트만 역직렬화한다. */
    private <T> T parseResponse(JsonNode response, Class<T> responseType) {
        if (response == null) throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asString())) {
                    throw new CustomException(OpenApiErrorCode.OPEN_API_REFUSED);
                }
            }
        }
        if (!"completed".equals(response.path("status").asString())) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_INCOMPLETE_RESPONSE);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asString())
                    || !"assistant".equals(output.path("role").asString())) continue;
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asString()) && content.path("text").isString()) {
                    text.append(content.path("text").asString());
                }
            }
        }
        if (text.isEmpty()) throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
        try {
            T result = jsonMapper.readerFor(responseType)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .readValue(text.toString());
            if (result == null) throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
            return result;
        } catch (JacksonException exception) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
        }
    }

    /** 외부 응답에서 진단에 필요한 필드만 추출한다. 손상되거나 과도한 본문은 기록하지 않는다. */
    private HttpFailure readHttpFailure(RestClientResponseException exception) {
        String code = "UNKNOWN";
        String type = "UNKNOWN";
        String param = "UNKNOWN";
        String message = "응답 상세 없음";
        String body = exception.getResponseBodyAsString();
        if (!body.isBlank() && body.length() <= 65_536) {
            try {
                JsonNode error = jsonMapper.readTree(body).path("error");
                code = safeToken(error.path("code").asString(""));
                type = safeToken(error.path("type").asString(""));
                param = safeToken(error.path("param").asString(""));
                message = safeMessage(error.path("message").asString("응답 상세 없음"));
            } catch (JacksonException exceptionIgnored) {
                /* JSON이 아닌 게이트웨이 오류 본문도 그대로 노출하지 않는다. */
            }
        }
        HttpHeaders headers = exception.getResponseHeaders();
        return new HttpFailure(exception.getStatusCode().value(), code, type, param,
                safeToken(headers == null ? null : headers.getFirst("x-request-id")), message,
                retryAfterMillis(headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER)));
    }

    /** 인증키·제어 문자를 진단 식별자로 기록하거나 외부 예외 데이터로 전달하지 않는다. */
    private String safeToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,128}") || value.startsWith("sk-")
                || apiKey != null && !apiKey.isBlank() && value.contains(apiKey)) return "UNKNOWN";
        return value;
    }

    /** 서버 로그용 오류 메시지에서 인증키를 가리고 한 줄 최대 512자로 제한한다. */
    private String safeMessage(String value) {
        String redacted = apiKey == null || apiKey.isBlank() ? value : value.replace(apiKey, "[REDACTED]");
        redacted = redacted.replaceAll("(?i)\\bBearer\\s+[^\\s\"']+", "Bearer [REDACTED]")
                .replaceAll("\\bsk-[A-Za-z0-9_-]+", "[REDACTED]").replaceAll("\\p{Cntrl}", " ");
        return redacted.substring(0, Math.min(512, redacted.length()));
    }

    /** Retry-After의 초 또는 HTTP 날짜를 밀리초로 변환한다. 잘못된 값은 백오프 정책에 맡긴다. */
    private Long retryAfterMillis(String value) {
        if (value == null || value.isBlank() || value.length() > 128) return null;
        try {
            BigDecimal seconds = new BigDecimal(value.strip());
            if (seconds.signum() < 0) return null;
            return seconds.multiply(new BigDecimal("1000")).setScale(0, RoundingMode.CEILING)
                    .min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            try {
                return Math.max(0, Duration.between(clock.instant(), ZonedDateTime.parse(value.strip(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant()).toMillis());
            } catch (DateTimeException | ArithmeticException invalidDate) {
                return null;
            }
        }
    }

    /** 외부 오류 메시지는 서버 로그에만 남기고 상태·코드·요청 ID 등 진단 메타데이터만 예외에 보관한다. */
    private CustomException httpFailure(OpenApiErrorCode code, HttpFailure failure) {
        return new CustomException(code, Map.of("upstreamStatus", failure.status(), "upstreamCode", failure.code(),
                "upstreamType", failure.type(), "requestId", failure.requestId(),
                "retryAfterMillis", failure.retryAfterMillis() == null ? 0L : failure.retryAfterMillis()));
    }

    /** 서버가 지정한 대기를 지키며 최대 60초까지 대기한다. 더 긴 대기는 조기 재시도 없이 호출자에게 반환한다. */
    private boolean awaitRetry(int attempt, Long retryAfterMillis) {
        if (retryAfterMillis != null && retryAfterMillis > MAX_RETRY_DELAY_MILLIS) {
            log.warn("[OpenApiUtil] 서버 대기 시간이 재시도 대기 상한을 초과했습니다. | retryAfterMillis: {}", retryAfterMillis);
            return false;
        }
        long backoff = Math.min(MAX_RETRY_DELAY_MILLIS, retryDelayMillis * (1L << (attempt - 1)));
        long jitter = backoff == 0 ? 0 : ThreadLocalRandom.current().nextLong(Math.min(500, backoff / 4) + 1);
        long delay = Math.max(retryAfterMillis == null ? 0 : retryAfterMillis, Math.min(MAX_RETRY_DELAY_MILLIS, backoff + jitter));
        log.info("[OpenApiUtil] 일시적 HTTP 오류 재시도 대기 | attempt: {}, delayMillis: {}", attempt, delay);
        try {
            sleeper.sleep(delay);
            checkInterrupted();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(OpenApiErrorCode.OPEN_API_INTERRUPTED);
        }
    }

    /** 워커 종료 요청을 HTTP 호출 전에 확인한다. */
    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_INTERRUPTED);
        }
    }

    /** HTTP 예외의 원인에서 시간 초과를 판별한다. */
    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private record HttpFailure(int status, String code, String type, String param, String requestId,
                               String message, Long retryAfterMillis) {}
}
