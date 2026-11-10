package com.lifelog.domain.residentialarea.controller;

import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.residentialarea.mapper.ResidentialAreaMapper;
import com.lifelog.domain.residentialarea.service.ResidentialAreaService;
import com.lifelog.global.config.SecurityConfig;
import com.lifelog.global.security.JwtAccessDeniedHandler;
import com.lifelog.global.security.JwtAuthenticationEntryPoint;
import com.lifelog.global.security.JwtAuthenticationFilter;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResidentialAreaController.class)
@ContextConfiguration(classes = ResidentialAreaControllerTest.TestConfiguration.class)
class ResidentialAreaControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private MemberRepository memberRepository;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("로그인한 사용자는 전체 17개 지역의 코드와 표시명을 순서대로 조회한다")
    void 전체지역_응답형식_성공() throws Exception {
        // given / when / then
        mvc.perform(get("/api/residential-areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("요청이 성공적으로 처리되었습니다."))
                .andExpect(jsonPath("$.data.residentialAreas.length()").value(17))
                .andExpect(jsonPath("$.data.residentialAreas[*].code", contains(
                        "SEOUL", "BUSAN", "DAEGU", "INCHEON", "GWANGJU", "DAEJEON", "ULSAN", "SEJONG",
                        "GYEONGGI", "GANGWON", "CHUNGBUK", "CHUNGNAM", "JEONBUK", "JEONNAM",
                        "GYEONGBUK", "GYEONGNAM", "JEJU")))
                .andExpect(jsonPath("$.data.residentialAreas[*].name", contains(
                        "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남",
                        "전북", "전남", "경북", "경남", "제주")));
    }

    @Test
    @DisplayName("인증 정보가 없으면 지역 목록 조회는 401을 반환한다")
    void 지역조회_실패_미인증() throws Exception {
        // given / when / then
        mvc.perform(get("/api/residential-areas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ResidentialAreaController.class, ResidentialAreaService.class, ResidentialAreaMapper.class,
            SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class})
    static class TestConfiguration {
    }
}
