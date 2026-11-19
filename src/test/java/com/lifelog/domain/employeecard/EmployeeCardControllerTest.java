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
import static org.mockito.ArgumentMatchers.eq;
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
    void 비로그인으로_사진과_정보를_보내면_PNG를_그대로_반환() throws Exception {
        given(imageClient.edit(any(), any(), any())).willReturn(CHARACTER);

        mockMvc.perform(request(photo("photo.jpg", MediaType.IMAGE_JPEG_VALUE, 100)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(CHARACTER));

        verify(imageClient).edit(any(), eq(MediaType.IMAGE_JPEG), argThat(prompt -> !prompt.contains("Additional")));
    }

    @Test
    void 외형_설명이_있으면_프롬프트에_포함() throws Exception {
        given(imageClient.edit(any(), any(), any())).willReturn(CHARACTER);

        mockMvc.perform(request(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100))
                        .param("appearance", "검은 단발머리,\n 둥근 안경"))
                .andExpect(status().isOk());

        verify(imageClient).edit(any(), any(), argThat(prompt -> prompt.contains("검은 단발머리, 둥근 안경")));
    }

    @Test
    void 사진이_없으면_400() throws Exception {
        mockMvc.perform(multipart(URL)
                        .param("name", "홍길동").param("school", "한국대학교").param("department", "컴퓨터공학과")
                        .with(anonymous()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verify(imageClient, never()).edit(any(), any(), any());
    }

    @Test
    void 이름이_비었거나_너무_길면_400() throws Exception {
        mockMvc.perform(multipart(URL).file(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100))
                        .param("name", " ").param("school", "한국대학교").param("department", "컴퓨터공학과")
                        .with(anonymous()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").exists());

        mockMvc.perform(multipart(URL).file(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100))
                        .param("name", "가".repeat(21)).param("school", "한국대학교").param("department", "컴퓨터공학과")
                        .with(anonymous()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미지가_아닌_파일이면_JSON_에러_400() throws Exception {
        mockMvc.perform(request(photo("photo.gif", MediaType.IMAGE_GIF_VALUE, 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CARD_400"));

        verify(imageClient, never()).edit(any(), any(), any());
    }

    @Test
    void 빈_사진이면_400() throws Exception {
        mockMvc.perform(request(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CARD_400"));
    }

    @Test
    void OpenAI가_거절하면_그_에러코드로_응답() throws Exception {
        given(imageClient.edit(any(), any(), any())).willThrow(new CustomException(OpenApiErrorCode.OPEN_API_REFUSED));

        mockMvc.perform(request(photo("photo.png", MediaType.IMAGE_PNG_VALUE, 100)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("OPEN_API_REFUSED"));
    }

    private MockMultipartHttpServletRequestBuilder request(MockMultipartFile file) {
        return (MockMultipartHttpServletRequestBuilder) multipart(URL).file(file)
                .param("name", "홍길동").param("school", "한국대학교").param("department", "컴퓨터공학과")
                .with(anonymous());
    }

    private MockMultipartFile photo(String filename, String contentType, int size) {
        return new MockMultipartFile("photo", filename, contentType, new byte[size]);
    }
}
