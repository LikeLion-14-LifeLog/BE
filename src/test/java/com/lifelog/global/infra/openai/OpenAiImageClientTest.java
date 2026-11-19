package com.lifelog.global.infra.openai;

import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 실제 HTTP 메시지 변환(multipart 인코딩)까지 거쳐 OpenAI 요청·응답 처리를 검증
class OpenAiImageClientTest {

    private static final byte[] PHOTO = {10, 20, 30};
    private static final byte[] RESULT = {1, 2, 3, 4};

    private MockRestServiceServer server;
    private OpenAiImageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiImageClient(builder.build(), "test-key", "image-model", "1024x1024", "medium");
    }

    @Test
    void multipart로_사진과_옵션을_보내고_b64_결과를_디코딩() {
        server.expect(requestTo("https://api.openai.test/v1/images/edits"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("Content-Type", containsString("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"model\"")))
                .andExpect(content().string(containsString("image-model")))
                .andExpect(content().string(containsString("name=\"image\"; filename=\"photo.png\"")))
                .andExpect(content().string(containsString("Content-Type: image/png")))
                .andRespond(withSuccess("{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(RESULT) + "\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.edit(PHOTO, MediaType.IMAGE_PNG, "prompt")).containsExactly(RESULT);
        server.verify();
    }

    @Test
    void 부적절한_이미지로_거절되면_REFUSED() {
        server.expect(requestTo("https://api.openai.test/v1/images/edits"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"moderation_blocked\",\"message\":\"blocked\"}}"));

        assertThatThrownBy(() -> client.edit(PHOTO, MediaType.IMAGE_PNG, "prompt"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OpenApiErrorCode.OPEN_API_REFUSED);
    }

    @Test
    void 요청량_제한이면_RATE_LIMITED() {
        server.expect(requestTo("https://api.openai.test/v1/images/edits"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.edit(PHOTO, MediaType.IMAGE_PNG, "prompt"))
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OpenApiErrorCode.OPEN_API_RATE_LIMITED);
    }

    @Test
    void 결과_이미지가_없으면_INVALID_RESPONSE() {
        server.expect(requestTo("https://api.openai.test/v1/images/edits"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.edit(PHOTO, MediaType.IMAGE_PNG, "prompt"))
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OpenApiErrorCode.OPEN_API_INVALID_RESPONSE);
    }

    @Test
    void 키가_없으면_호출하지_않고_KEY_NOT_CONFIGURED() {
        OpenAiImageClient noKey = new OpenAiImageClient(RestClient.create(), "", "m", "1024x1024", "medium");

        assertThatThrownBy(() -> noKey.edit(PHOTO, MediaType.IMAGE_PNG, "prompt"))
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OpenApiErrorCode.OPEN_API_KEY_NOT_CONFIGURED);
    }
}
