package com.lifelog.domain.employeecard;

import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenAiImageClient;
import com.lifelog.global.infra.openai.OpenApiErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmployeeCardControllerTest {

    private static final String URL = "/api/employee-cards";
    private static final byte[] CHARACTER = {1, 2, 3, 4};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenAiImageClient imageClient;

    @Test
    void 비로그인으로_사진과_성별을_보내면_PNG를_그대로_반환() throws Exception {
        given(imageClient.edit(any(), any())).willReturn(CHARACTER);

        mockMvc.perform(request(photo("photo.jpg", MediaType.IMAGE_JPEG_VALUE, 100), "FEMALE"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(CHARACTER));

        // 첫 번째는 사용자 사진, 그 뒤는 참고 이미지 폴더의 파일 이름순
        verify(imageClient).edit(argThat(images -> images.size() == 3
                        && images.get(0).contentType().equals(MediaType.IMAGE_JPEG)
                        && images.get(0).bytes().length == 100
                        && images.get(1).filename().equals("01-style.jpg")
                        && images.get(1).contentType().equals(MediaType.IMAGE_JPEG)
                        && images.get(2).filename().equals("02-style.png")
                        && images.get(2).contentType().equals(MediaType.IMAGE_PNG)),
                argThat(prompt -> prompt.contains("세미 리얼리스틱") && prompt.contains("캐릭터의 성별: 여성")));
    }

    @Test
    void 남성이면_프롬프트에_남성으로_반영() throws Exception {
        given(imageClient.edit(any(), any())).willReturn(CHARACTER);

        mockMvc.perform(request(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100), "MALE"))
                .andExpect(status().isOk());

        verify(imageClient).edit(any(), argThat(prompt -> prompt.contains("캐릭터의 성별: 남성")));
    }

    @Test
    void 성별이_없거나_잘못된_값이면_400() throws Exception {
        mockMvc.perform(multipart(URL).file(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100)).with(anonymous()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.data.gender").exists());

        mockMvc.perform(request(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100), "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verify(imageClient, never()).edit(any(), any());
    }

    @Test
    void 사진이_없으면_400() throws Exception {
        mockMvc.perform(multipart(URL).param("gender", "MALE").with(anonymous()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verify(imageClient, never()).edit(any(), any());
    }

    @Test
    void 이미지가_아닌_파일이면_JSON_에러_400() throws Exception {
        mockMvc.perform(request(photo("photo.gif", MediaType.IMAGE_GIF_VALUE, 100), "MALE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CARD_400"));

        verify(imageClient, never()).edit(any(), any());
    }

    @Test
    void 빈_사진이면_400() throws Exception {
        mockMvc.perform(request(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 0), "MALE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CARD_400"));
    }

    @Test
    void OpenAI가_거절하면_그_에러코드로_응답() throws Exception {
        given(imageClient.edit(any(), any())).willThrow(new CustomException(OpenApiErrorCode.OPEN_API_REFUSED));

        mockMvc.perform(request(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100), "MALE"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("OPEN_API_REFUSED"));
    }

    private MockMultipartHttpServletRequestBuilder request(MockMultipartFile file, String gender) {
        return (MockMultipartHttpServletRequestBuilder) multipart(URL).file(file)
                .param("gender", gender)
                .with(anonymous());
    }

    private MockMultipartFile photo(String filename, String contentType, int size) {
        return new MockMultipartFile("photo", filename, contentType, new byte[size]);
    }
}
