package com.lifelog.domain.certification;
import com.lifelog.domain.certification.controller.CertificationController;
import com.lifelog.domain.certification.dto.CertificationInitResDto;
import com.lifelog.domain.certification.service.CertificationService;
import com.lifelog.global.config.SecurityConfig;
import com.lifelog.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(CertificationController.class)
@ContextConfiguration(classes = CertificationControllerTest.TestConfiguration.class)
class CertificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CertificationService service;
    @Test @DisplayName("Body와 인증 없이 초기화 집계를 공통 형식으로 반환한다")
    void 초기화_성공() throws Exception {
        // given
        given(service.initFromCsv()).willReturn(CertificationInitResDto.create(1424,1424,0,0));
        // when / then
        mvc.perform(put("/api/admin/certifications/init"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("COMMON_200"))
            .andExpect(jsonPath("$.message").value("[관리자] 자격증 데이터 초기화 성공"))
            .andExpect(jsonPath("$.data.totalCount").value(1424))
            .andExpect(jsonPath("$.data.createdCount").value(1424))
            .andExpect(jsonPath("$.data.updatedCount").value(0))
            .andExpect(jsonPath("$.data.unchangedCount").value(0));
    }
    @Test @DisplayName("식별 충돌은 명세의 CONFLICT 코드로 반환한다")
    void 식별충돌_실패() throws Exception {
        // given
        given(service.initFromCsv()).willThrow(new com.lifelog.global.exception.CustomException(
            com.lifelog.domain.certification.exception.CertificationErrorCode.IDENTITY_CONFLICT));
        // when / then
        mvc.perform(put("/api/admin/certifications/init"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }
    @Configuration(proxyBeanMethods = false)
    @Import({CertificationController.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class TestConfiguration {}
}
