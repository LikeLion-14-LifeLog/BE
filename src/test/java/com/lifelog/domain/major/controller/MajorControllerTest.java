package com.lifelog.domain.major.controller;

import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.domain.major.dto.MajorListResDto;
import com.lifelog.domain.major.dto.MajorResDto;
import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.service.MajorService;
import com.lifelog.global.config.SecurityConfig;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.exception.GlobalExceptionHandler;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.response.SliceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MajorController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = MajorControllerTest.TestConfiguration.class)
class MajorControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private MajorService service;
    @MockitoBean
    private com.lifelog.global.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private com.lifelog.global.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    @DisplayName("기본 조회는 ApiResponse 내부에 majors와 페이징 정보를 펼쳐서 반환한다")
    void 기본조회_응답형식_성공() throws Exception {
        // given
        var major = MajorResDto.create(101L, MajorMainField.ENGINEERING, "컴퓨터ㆍ통신", "전산학ㆍ컴퓨터공학",
                "컴퓨터공학과", "U04080100061", DegreeProcess.UNIVERSITY);
        given(service.searchMajors(null, null, 1, 20)).willReturn(SliceResponse.of(
                new PageImpl<>(List.of(major), PageRequest.of(0, 20), 1), MajorListResDto.create(List.of(major))));
        // when / then
        mvc.perform(get("/api/majors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("요청이 성공적으로 처리되었습니다."))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.majors[0].majorId").value(101))
                .andExpect(jsonPath("$.data.majors[0].mainField").value("ENGINEERING"))
                .andExpect(jsonPath("$.data.majors[0].middleField").value("컴퓨터ㆍ통신"))
                .andExpect(jsonPath("$.data.majors[0].subField").value("전산학ㆍ컴퓨터공학"))
                .andExpect(jsonPath("$.data.majors[0].name").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.data.majors[0].code").value("U04080100061"))
                .andExpect(jsonPath("$.data.majors[0].degreeProcessName").value("UNIVERSITY"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("쿼리 조건을 바인딩하고 빈 결과는 빈 배열로 반환한다")
    void 필터_빈결과_성공() throws Exception {
        // given
        given(service.searchMajors("검색", MajorMainField.ENGINEERING, 2, 10)).willReturn(SliceResponse.of(
                new PageImpl<>(List.of(), PageRequest.of(1, 10), 0), MajorListResDto.create(List.of())));
        // when / then
        mvc.perform(get("/api/majors").param("keyword", "검색").param("mainField", "ENGINEERING")
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors").isEmpty())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("잘못된 enum과 숫자 형식은 공통 400 응답을 반환한다")
    void 쿼리바인딩_실패_타입오류() throws Exception {
        // given / when / then
        for (String param : new String[]{"mainField", "page", "size"}) {
            mvc.perform(get("/api/majors").param(param, "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_400"));
        }
    }

    @Test
    @DisplayName("페이지 번호와 크기 검증 실패는 공통 400 응답을 반환한다")
    void 검색조건_실패_검증오류() throws Exception {
        // given
        given(service.searchMajors(null, null, 0, 20))
                .willThrow(new CustomException(CommonErrorCode.INVALID_PAGE));
        given(service.searchMajors(null, null, 1, 101))
                .willThrow(new CustomException(CommonErrorCode.INVALID_SIZE));
        // when / then
        mvc.perform(get("/api/majors").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_1"))
                .andExpect(jsonPath("$.message").value("페이지 번호는 1 이상이어야 합니다."));
        mvc.perform(get("/api/majors").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_2"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
    }

    @Test
    @DisplayName("관리자 초기화 API의 기존 경로가 유지된다")
    void 초기화_기존경로_성공() throws Exception {
        // given
        given(service.initFromCsv()).willReturn(MajorInitResDto.create(0, 0, 0, 0, 0, 0));
        // when / then
        mvc.perform(put("/api/admin/majors/init"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MajorController.class, GlobalExceptionHandler.class, SecurityConfig.class})
    static class TestConfiguration {
    }
}
