package com.lifelog.global.infra.openai;

import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * OpenAI Images API(이미지 편집) 호출만 담당한다.
 * 이미지 생성은 오래 걸리고 비용이 커서 재시도하지 않는다.
 */
@Slf4j
@Component
public class OpenAiImageClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String size;
    private final String quality;

    @Autowired
    public OpenAiImageClient(RestClient.Builder builder,
                             @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
                             @Value("${openai.image.api-key:}") String apiKey,
                             @Value("${openai.image.model:gpt-image-1}") String model,
                             @Value("${openai.image.size:1024x1024}") String size,
                             @Value("${openai.image.quality:medium}") String quality,
                             @Value("${openai.connect-timeout:5s}") Duration connectTimeout,
                             @Value("${openai.image.read-timeout:180s}") Duration readTimeout) {
        this(configureClient(builder, baseUrl, connectTimeout, readTimeout), apiKey, model, size, quality);
    }

    // 테스트에서 MockRestServiceServer에 연결한 RestClient를 주입
    OpenAiImageClient(RestClient restClient, String apiKey, String model, String size, String quality) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
        this.size = size;
        this.quality = quality;
    }

    /**
     * 입력 이미지들을 프롬프트대로 편집한다.
     * @param images 입력 이미지 (보낸 순서대로 첫 번째, 두 번째… 이미지가 됨)
     * @param prompt 편집 지시
     * @return 결과 이미지(PNG) 바이트
     */
    public byte[] edit(List<OpenAiImageInput> images, String prompt) {
        log.info("[OpenAiImageClient] 이미지 편집 요청 | edit() - START | model: {}, size: {}, quality: {}, images: {}",
                model, size, quality, images.size());
        if (apiKey == null || apiKey.isBlank()) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_KEY_NOT_CONFIGURED);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", model);
        body.add("prompt", prompt);
        body.add("size", size);
        body.add("quality", quality);
        body.add("n", "1");
        // 여러 장은 OpenAI 규격대로 image[] 필드를 반복
        String imageField = images.size() > 1 ? "image[]" : "image";
        for (OpenAiImageInput image : images) {
            // MultipartBodyBuilder는 WebFlux(reactive-streams) 의존이라 MVC 환경에서 쓰지 않음
            HttpHeaders imageHeaders = new HttpHeaders();
            imageHeaders.setContentType(image.contentType());
            imageHeaders.setContentDisposition(ContentDisposition.formData()
                    .name(imageField).filename(image.filename()).build());
            body.add(imageField, new HttpEntity<>(new ByteArrayResource(image.bytes()), imageHeaders));
        }

        JsonNode response;
        try {
            response = restClient.post().uri("/images/edits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String code = errorCode(exception);
            // 요청 본문(사진)은 기록하지 않음. 응답 메시지는 원인 파악용으로 기록
            log.warn("[OpenAiImageClient] HTTP 호출 실패 | status: {}, errorCode: {}, model: {}, message: {}",
                    status, code, model, errorMessage(exception));
            if ("moderation_blocked".equals(code)) {
                throw new CustomException(OpenApiErrorCode.OPEN_API_REFUSED);
            }
            if ("insufficient_quota".equals(code) || "billing_hard_limit_reached".equals(code)) {
                throw new CustomException(OpenApiErrorCode.OPEN_API_QUOTA_EXCEEDED);
            }
            throw new CustomException(status == 429
                    ? OpenApiErrorCode.OPEN_API_RATE_LIMITED : OpenApiErrorCode.OPEN_API_REQUEST_FAILED);
        } catch (ResourceAccessException exception) {
            log.warn("[OpenAiImageClient] 연결 실패 또는 시간 초과 | {}", exception.getClass().getSimpleName());
            throw new CustomException(OpenApiErrorCode.OPEN_API_TIMEOUT);
        } catch (RestClientException exception) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
        }

        JsonNode b64 = response == null ? null : response.path("data").path(0).path("b64_json");
        if (b64 == null || !b64.isString() || b64.asString().isBlank()) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
        }
        try {
            byte[] result = Base64.getDecoder().decode(b64.asString());
            log.info("[OpenAiImageClient] 이미지 편집 요청 | edit() - END | bytes: {}", result.length);
            return result;
        } catch (IllegalArgumentException exception) {
            throw new CustomException(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
        }
    }

    private static RestClient configureClient(RestClient.Builder builder, String baseUrl,
                                              Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    // 오류 응답 본문의 error.message (읽지 못하면 null)
    private String errorMessage(RestClientResponseException exception) {
        try {
            JsonNode error = exception.getResponseBodyAs(JsonNode.class);
            JsonNode message = error == null ? null : error.path("error").path("message");
            return message != null && message.isString() ? message.asString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // 오류 응답 본문의 error.code (읽지 못하면 null)
    private String errorCode(RestClientResponseException exception) {
        try {
            JsonNode error = exception.getResponseBodyAs(JsonNode.class);
            JsonNode code = error == null ? null : error.path("error").path("code");
            return code != null && code.isString() ? code.asString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
