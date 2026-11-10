package com.lifelog.global.security;

import com.lifelog.domain.certification.service.CertificationCsvReader;
import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.service.MajorCsvReader;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-authorization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CatalogAuthorizationIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private CertificationCsvReader certificationCsvReader;
    @MockitoBean private MajorCsvReader majorCsvReader;

    private Member user;
    private Member admin;
    private Member inactiveMember;
    private String userToken;
    private String adminToken;
    private String inactiveToken;

    @BeforeAll
    void prepareAuthentication() {
        user = memberRepository.saveAndFlush(new Member("catalog-user@example.com"));
        admin = memberRepository.saveAndFlush(new Member("catalog-admin@example.com"));
        inactiveMember = memberRepository.saveAndFlush(new Member("catalog-inactive@example.com"));
        jdbc.update("UPDATE member SET role='ADMIN' WHERE member_id=?", admin.getId());
        jdbc.update("UPDATE member SET status='INACTIVE' WHERE member_id=?", inactiveMember.getId());
        userToken = jwtProvider.createAccessToken(user.getId());
        adminToken = jwtProvider.createAccessToken(admin.getId());
        inactiveToken = jwtProvider.createAccessToken(inactiveMember.getId());
        jdbc.execute("CREATE TABLE IF NOT EXISTS certification_init_lock(id INTEGER PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS major_init_lock(id INTEGER PRIMARY KEY)");
        jdbc.execute("MERGE INTO certification_init_lock KEY(id) VALUES (1)");
        jdbc.execute("MERGE INTO major_init_lock KEY(id) VALUES (1)");
    }

    @AfterAll
    void cleanDatabase() {
        memberRepository.deleteAllById(List.of(user.getId(), admin.getId(), inactiveMember.getId()));
        jdbc.execute("DROP TABLE certification_init_lock");
        jdbc.execute("DROP TABLE major_init_lock");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/certifications", "/api/majors"})
    @DisplayName("목록 조회는 인증이 없으면 401을 반환한다")
    void 조회_실패_미인증(String path) throws Exception {
        // given / when / then
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/certifications", "/api/majors"})
    @DisplayName("일반 회원의 JWT로 목록을 조회한다")
    void 조회_일반회원_성공(String path) throws Exception {
        // given / when / then
        mvc.perform(get(path).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/certifications", "/api/majors"})
    @DisplayName("관리자도 JWT로 목록을 조회한다")
    void 조회_관리자_성공(String path) throws Exception {
        // given / when / then
        mvc.perform(get(path).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/certifications/init", "/api/admin/majors/init"})
    @DisplayName("초기화는 인증이 없으면 401을 반환한다")
    void 초기화_실패_미인증(String path) throws Exception {
        // given / when / then
        mvc.perform(put(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/certifications/init", "/api/admin/majors/init"})
    @DisplayName("일반 회원의 JWT로 초기화하면 403을 반환한다")
    void 초기화_실패_일반회원(String path) throws Exception {
        // given / when / then
        mvc.perform(put(path).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/certifications/init", "/api/admin/majors/init"})
    @DisplayName("관리자의 JWT로 초기화하면 실제 초기화 결과를 반환한다")
    void 초기화_관리자_성공(String path) throws Exception {
        // given
        given(certificationCsvReader.read()).willReturn(List.of());
        given(majorCsvReader.read()).willReturn(new MajorCsvReadResult(0, 0, List.of()));
        // when / then
        mvc.perform(put(path).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/certifications", "/api/majors"})
    @DisplayName("유효하지 않은 JWT로 조회하면 401을 반환한다")
    void 조회_실패_잘못된토큰(String path) throws Exception {
        // given / when / then
        mvc.perform(get(path).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/certifications/init", "/api/admin/majors/init"})
    @DisplayName("비활성 회원의 유효한 JWT로 초기화하면 401을 반환한다")
    void 초기화_실패_비활성회원(String path) throws Exception {
        // given / when / then
        mvc.perform(put(path).header("Authorization", "Bearer " + inactiveToken))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/certifications/init", "/api/admin/majors/init"})
    @DisplayName("관리자 권한을 회수하면 기존 JWT로 초기화할 수 없다")
    void 초기화_실패_관리자권한회수(String path) throws Exception {
        // given
        jdbc.update("UPDATE member SET role='USER' WHERE member_id=?", admin.getId());
        try {
            // when / then
            mvc.perform(put(path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_403"));
        } finally {
            jdbc.update("UPDATE member SET role='ADMIN' WHERE member_id=?", admin.getId());
        }
    }
}
