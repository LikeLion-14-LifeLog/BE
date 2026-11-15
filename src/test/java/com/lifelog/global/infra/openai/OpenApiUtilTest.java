package com.lifelog.global.infra.openai;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class OpenApiUtilTest {
    private static final String BASE_URL = "https://openai.test/v1";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private OpenApiUtil client;
    private RestClient restClient;

    @BeforeEach
    void 준비() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new OpenApiUtil(restClient, jsonMapper, "TEST_KEY", "gpt-4.1-mini", 3, 0);
    }

    @Test
    @DisplayName("Responses API에 역할과 스키마를 분리해 보내고 output 배열의 JSON을 읽는다")
    void 구조화응답_HTTP계약_성공() {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TEST_KEY"))
                .andExpect(request -> {
                    JsonNode body = jsonMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("model").asString()).isEqualTo("gpt-4.1-mini");
                    assertThat(body.path("store").asBoolean()).isFalse();
                    assertThat(body.path("input").get(0).path("role").asString()).isEqualTo("developer");
                    assertThat(body.path("input").get(1).path("content").asString()).isEqualTo("{\"sampleCount\":10}");
                    assertThat(body.path("text").path("format").path("type").asString()).isEqualTo("json_schema");
                    assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
                }).andRespond(withSuccess(completed(validJson()), MediaType.APPLICATION_JSON));
        // when
        AnnualSalaryAnalyzationAiResDto result = client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class);
        // then
        assertThat(result.summary()).isEqualTo("요약");
        assertThat(result.salaryResults()).hasSize(3);
        assertThat(result.salaryResults().getFirst().annualIncomeExpected()).isEqualTo(36000000L);
        server.verify();
    }

    @Test
    @DisplayName("429와 5xx만 제한 횟수 안에서 재시도한다")
    void 일시적HTTP오류_재시도후_성공() {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withSuccess(completed(validJson()), MediaType.APPLICATION_JSON));
        // when / then
        assertThat(client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class).summary()).isEqualTo("요약");
        server.verify();
    }

    @Test
    @DisplayName("계속된 5xx 오류는 최대 3회만 요청하고 실패한다")
    void 재시도한도초과_호출_실패() {
        // given
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_REQUEST_FAILED);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"insufficient_quota", "billing_hard_limit_reached", "credit_balance_exhausted",
            "organization_spend_limit_exceeded", "project_spend_limit_exceeded", "organization_usage_limit_exceeded"})
    @DisplayName("429 결제·잔액·사용 한도 오류는 코드별로 구분하고 한 번만 요청한다")
    void 사용한도초과_재시도없이_실패(String code) {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("x-request-id", "req_quota_test").contentType(MediaType.APPLICATION_JSON)
                .body(providerError(code, "billing_error", "PRIVATE_PROVIDER_MESSAGE")));
        // when / then
        assertThatThrownBy(() -> client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class))
                .isInstanceOfSatisfying(CustomException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OpenApiErrorCode.OPEN_API_QUOTA_EXCEEDED);
                    assertThat(error.getData()).isEqualTo(Map.of("upstreamStatus", 429, "upstreamCode", code,
                            "upstreamType", "billing_error", "requestId", "req_quota_test", "retryAfterMillis", 0L));
                }).hasMessageNotContaining("PRIVATE_PROVIDER_MESSAGE");
        server.verify();
    }

    @Test
    @DisplayName("코드가 누락되어도 insufficient_quota 유형이면 재시도하지 않는다")
    void 사용한도유형만존재_재시도없이_실패() {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body(providerError("", "insufficient_quota", "Quota exceeded")));
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_QUOTA_EXCEEDED);
        server.verify();
    }

    @Test
    @DisplayName("계속된 일시적 429는 최대 횟수 이후 사용 한도 오류와 다른 코드로 반환한다")
    void 일시적요청량제한_재시도한도초과_실패() {
        // given
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON).body(providerError("rate_limit_exceeded", "rate_limit_error", "Too many tokens")));
        }
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_RATE_LIMITED);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource(value = {"2|2000", "0.25|250", "Fri, 13 Nov 2026 04:33:03 GMT|3000",
            "Fri, 13 Nov 2026 04:32:00 GMT|0", "invalid|0", "-1|0"}, delimiter = '|')
    @DisplayName("Retry-After의 초·HTTP 날짜를 지키고 잘못된 값은 기본 백오프로 처리한다")
    void 서버재시도간격준수_재시도후_성공(String retryAfter, long expectedMillis) {
        // given
        List<Long> delays = new ArrayList<>();
        client = new OpenApiUtil(restClient, jsonMapper, "TEST_KEY", "gpt-4.1-mini", 3, 0,
                Clock.fixed(Instant.parse("2026-11-13T04:33:00Z"), ZoneOffset.UTC), delays::add);
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, retryAfter));
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withSuccess(completed(validJson()), MediaType.APPLICATION_JSON));
        // when
        AnnualSalaryAnalyzationAiResDto result = client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class);
        // then
        assertThat(result.summary()).isEqualTo("요약");
        assertThat(delays).containsExactly(expectedMillis);
        server.verify();
    }

    @Test
    @DisplayName("서버 대기가 60초를 넘으면 대기를 단축해 재시도하지 않고 제한 오류를 반환한다")
    void 긴서버대기_조기재시도없이_실패() {
        // given
        List<Long> delays = new ArrayList<>();
        client = new OpenApiUtil(restClient, jsonMapper, "TEST_KEY", "gpt-4.1-mini", 3, 0, Clock.systemUTC(), delays::add);
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "61"));
        // when / then
        assertThatThrownBy(() -> client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class))
                .isInstanceOfSatisfying(CustomException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OpenApiErrorCode.OPEN_API_RATE_LIMITED);
                    assertThat(error.getData()).isInstanceOfSatisfying(Map.class,
                            details -> assertThat(details.get("retryAfterMillis")).isEqualTo(61000L));
                });
        assertThat(delays).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Retry-After가 없으면 대기 간격을 지수적으로 늘리되 테스트에서는 실제로 대기하지 않는다")
    void 지수백오프와지터_재시도후_성공() {
        // given
        List<Long> delays = new ArrayList<>();
        client = new OpenApiUtil(restClient, jsonMapper, "TEST_KEY", "gpt-4.1-mini", 3, 2000, Clock.systemUTC(), delays::add);
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withSuccess(completed(validJson()), MediaType.APPLICATION_JSON));
        // when
        client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class);
        // then
        assertThat(delays).hasSize(2);
        assertThat(delays.get(0)).isBetween(2000L, 2500L);
        assertThat(delays.get(1)).isBetween(4000L, 4500L);
        server.verify();
    }

    @Test
    @DisplayName("오류 코드·요청 ID를 기록하면서 인증키·제어 문자·원본 본문은 노출하지 않는다")
    void HTTP오류진단과인증키마스킹_성공(CapturedOutput output) {
        // given
        String message = "Too many requests TEST_KEY sk-proj-OTHER_SECRET Bearer OTHER_TOKEN\nINJECTED_LINE";
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("x-request-id", "req_diagnostic_test").contentType(MediaType.APPLICATION_JSON)
                .body(providerError("credit_balance_exhausted", "insufficient_quota", message)));
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_QUOTA_EXCEEDED);
        assertThat(output.getAll()).contains("errorCode: credit_balance_exhausted", "requestId: req_diagnostic_test", "[REDACTED]")
                .doesNotContain("TEST_KEY", "sk-proj-OTHER_SECRET", "Bearer OTHER_TOKEN", "\nINJECTED_LINE", "sampleCount");
        server.verify();
    }

    @Test
    @DisplayName("재시도 대기가 중단되면 인터럽트 상태를 보존하고 다음 요청을 보내지 않는다")
    void 재시도대기인터럽트_호출_실패() {
        // given
        client = new OpenApiUtil(restClient, jsonMapper, "TEST_KEY", "gpt-4.1-mini", 3, 0, Clock.systemUTC(),
                millis -> { throw new InterruptedException("worker stop"); });
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        try {
            // when / then
            assertError(OpenApiErrorCode.OPEN_API_INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            server.verify();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    @DisplayName("요청·인증 오류는 재시도하지 않고 외부 본문을 예외에 노출하지 않는다")
    void 영구HTTP오류_호출_실패(int status) {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("PRIVATE_PROVIDER_ERROR").contentType(MediaType.APPLICATION_JSON));
        // when / then
        assertThatThrownBy(() -> client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class))
                .isInstanceOfSatisfying(CustomException.class, error -> assertThat(error.getErrorCode()).isEqualTo(OpenApiErrorCode.OPEN_API_REQUEST_FAILED))
                .hasMessageNotContaining("PRIVATE_PROVIDER_ERROR");
        server.verify();
    }

    @Test
    @DisplayName("시간 초과는 명확한 오류 코드로 반환하고 재시도하지 않는다")
    void 응답시간초과_호출_실패() {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withException(new SocketTimeoutException("read timed out")));
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_TIMEOUT);
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 HTTP 요청을 보내지 않는다")
    void 인증키누락_호출_실패() {
        // given
        client = new OpenApiUtil(restClient, jsonMapper, "", "gpt-4.1-mini", 3, 0);
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_KEY_NOT_CONFIGURED);
        server.verify();
    }

    @Test
    @DisplayName("모델의 refusal 응답을 일반 JSON 응답과 구분한다")
    void 모델거절_호출_실패() {
        // given
        String response = jsonMapper.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of(
                "type", "message", "role", "assistant", "content", List.of(Map.of("type", "refusal", "refusal", "denied"))))));
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_REFUSED);
        server.verify();
    }

    @Test
    @DisplayName("부분 출력이 유효한 JSON이어도 incomplete 상태이면 거절한다")
    void 미완료응답_호출_실패() {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withSuccess(
                completed(validJson()).replace("\"status\":\"completed\"", "\"status\":\"incomplete\""), MediaType.APPLICATION_JSON));
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_INCOMPLETE_RESPONSE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{} {}", "{\"careerLabel\":\"unknown\"}",
            "{\"salaryResults\":[{\"annual\":0,\"annualIncomeExpected\":12.5}]}"})
    @DisplayName("빈 텍스트·손상 JSON·추가 필드·소수 금액을 응답 DTO로 허용하지 않는다")
    void 잘못된출력형식_호출_실패(String text) {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withSuccess(completed(text), MediaType.APPLICATION_JSON));
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
        server.verify();
    }

    @Test
    @DisplayName("정수 범위를 넘는 금액은 잘라내거나 반올림하지 않고 거절한다")
    void 금액오버플로_호출_실패() {
        // given
        server.expect(requestTo(BASE_URL + "/responses")).andRespond(withSuccess(
                completed(validJson().replace("36000000", "9223372036854775808")), MediaType.APPLICATION_JSON));
        // when / then
        assertError(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
    }

    private OpenApiPrompt prompt() {
        return new OpenApiPrompt("연봉 분석 지시", "{\"sampleCount\":10}", "annual_salary_test",
                jsonMapper.readTree("{\"type\":\"object\",\"additionalProperties\":false}"));
    }

    private String providerError(String code, String type, String message) {
        return jsonMapper.writeValueAsString(Map.of("error", Map.of("code", code, "type", type, "message", message)));
    }

    private String completed(String text) {
        return jsonMapper.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of(
                "type", "message", "role", "assistant", "content", List.of(Map.of("type", "output_text", "text", text))))));
    }

    private String validJson() {
        return """
                {"summary":"요약","salaryResults":[
                  {"annual":0,"annualIncomeMin":34000000,"annualIncomeExpected":36000000,"annualIncomeMax":38000000},
                  {"annual":3,"annualIncomeMin":41000000,"annualIncomeExpected":44500000,"annualIncomeMax":48000000},
                  {"annual":5,"annualIncomeMin":50000000,"annualIncomeExpected":54500000,"annualIncomeMax":60000000}
                ],"comparisonGroupDescription":"비교 집단","recommendation":"보완점"}
                """;
    }

    private void assertError(OpenApiErrorCode expected) {
        assertThatThrownBy(() -> client.request(prompt(), AnnualSalaryAnalyzationAiResDto.class))
                .isInstanceOfSatisfying(CustomException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }
}
