package com.lifelog.domain.specprofile;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.global.infra.openai.OpenApiUtil;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:annual-run-latest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpecProfileAnalyzationRunLatestIntegrationTest {
    private static final String LATEST_RUN_URL = "/api/spec-profile/runs/latest";
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired JobRepository jobs;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @MockitoBean OpenApiUtil openApiUtil;
    Member member;
    SpecProfile profile;
    Job job;

    @BeforeEach
    void prepare() {
        member = members.saveAndFlush(new Member("latest-run@example.com"));
        profile = profiles.saveAndFlush(SpecProfile.builder().member(member).build());
        job = jobs.saveAndFlush(Job.builder().code("1332").name("개발자").mainCategoryName("연구직")
                .subCategoryCode("133").subCategoryName("개발직").build());
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"QUEUED", "IN_PROGRESS", "COMPLETED"})
    @DisplayName("대기·진행·완료 중 가장 최근 요청한 실행 ID만 명세 형식으로 반환한다")
    void 허용상태의최신실행조회_성공(SpecProfileAnalyzationStatus state) throws Exception {
        // given
        Long latestRunId = saveRun(profile, state, REQUESTED_AT.plusSeconds(1), null);
        saveRun(profile, SpecProfileAnalyzationStatus.COMPLETED, REQUESTED_AT, null);

        // when / then
        requestLatest(member.getId())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("runId 조회 성공"))
                .andExpect(jsonPath("$.data.runId").value(latestRunId))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"CANCELED", "FAILED"})
    @DisplayName("더 최근의 취소·실패 실행을 건너뛰고 조회 가능한 실행을 반환한다")
    void 취소실패실행제외_성공(SpecProfileAnalyzationStatus state) throws Exception {
        // given
        Long expectedRunId = saveRun(profile, SpecProfileAnalyzationStatus.COMPLETED, REQUESTED_AT, null);
        saveRun(profile, state, REQUESTED_AT.plusSeconds(1), null);

        // when / then
        requestLatest(member.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(expectedRunId));
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"CANCELED", "FAILED"})
    @DisplayName("취소·실패 실행만 있으면 조회 가능한 실행이 없으므로 404를 반환한다")
    void 취소실패실행만존재_실패_조회대상없음(SpecProfileAnalyzationStatus state) throws Exception {
        // given
        saveRun(profile, state, REQUESTED_AT, null);

        // when / then
        requestLatest(member.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("요청 시각이 같으면 더 큰 실행 PK를 반환한다")
    void 같은요청시각_PK내림차순조회_성공() throws Exception {
        // given
        saveRun(profile, SpecProfileAnalyzationStatus.COMPLETED, REQUESTED_AT, null);
        Long expectedRunId = saveRun(profile, SpecProfileAnalyzationStatus.QUEUED, REQUESTED_AT, null);

        // when / then
        requestLatest(member.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(expectedRunId));
    }

    @Test
    @DisplayName("타인에게 더 최근 실행이 있어도 본인의 최신 실행을 반환한다")
    void 타인실행제외_성공() throws Exception {
        // given
        Long expectedRunId = saveRun(profile, SpecProfileAnalyzationStatus.COMPLETED, REQUESTED_AT, null);
        saveRun(saveOtherProfile(), SpecProfileAnalyzationStatus.QUEUED, REQUESTED_AT.plusSeconds(1), null);

        // when / then
        requestLatest(member.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(expectedRunId));
    }

    @Test
    @DisplayName("타인의 실행만 존재하면 실행 ID를 노출하지 않고 404를 반환한다")
    void 타인실행만존재_실패_소유권없음() throws Exception {
        // given
        saveRun(saveOtherProfile(), SpecProfileAnalyzationStatus.COMPLETED, REQUESTED_AT, null);

        // when / then
        requestLatest(member.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제된 최신 실행을 건너뛰고 미삭제 실행을 반환한다")
    void 삭제된실행제외_성공() throws Exception {
        // given
        Long expectedRunId = saveRun(profile, SpecProfileAnalyzationStatus.COMPLETED, REQUESTED_AT, null);
        saveRun(profile, SpecProfileAnalyzationStatus.QUEUED, REQUESTED_AT.plusSeconds(1), REQUESTED_AT.plusSeconds(2));

        // when / then
        requestLatest(member.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(expectedRunId));
    }

    @Test
    @DisplayName("삭제된 실행만 있으면 404를 반환한다")
    void 삭제된실행만존재_실패_조회대상없음() throws Exception {
        // given
        saveRun(profile, SpecProfileAnalyzationStatus.COMPLETED, REQUESTED_AT, REQUESTED_AT.plusSeconds(1));

        // when / then
        requestLatest(member.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("분석을 한 번도 요청하지 않은 회원은 404를 반환한다")
    void 실행이력없음_실패() throws Exception {
        // given / when / then
        requestLatest(member.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer invalid-token"})
    @DisplayName("인증 정보가 없거나 올바르지 않으면 401을 반환한다")
    void 미인증조회_실패(String authorization) throws Exception {
        // given / when / then
        mvc.perform(get(LATEST_RUN_URL).header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    private SpecProfile saveOtherProfile() {
        Member other = members.saveAndFlush(new Member("other-latest-run@example.com"));
        return profiles.saveAndFlush(SpecProfile.builder().member(other).build());
    }

    private Long saveRun(SpecProfile ownerProfile, SpecProfileAnalyzationStatus state,
                         LocalDateTime createdAt, LocalDateTime deleteAt) {
        return runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(ownerProfile).job(job)
                .status(state).createdAt(createdAt).deleteAt(deleteAt).specProfileSnapshotJson("{}").build()).getId();
    }

    private ResultActions requestLatest(Long memberId) throws Exception {
        return mvc.perform(get(LATEST_RUN_URL).header("Authorization", "Bearer " + jwt.createAccessToken(memberId)));
    }
}
