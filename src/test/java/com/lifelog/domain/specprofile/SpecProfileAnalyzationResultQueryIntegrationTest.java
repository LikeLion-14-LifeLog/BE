package com.lifelog.domain.specprofile;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.service.SpecProfileAnnualSalaryResultSaveService;
import com.lifelog.global.infra.openai.OpenApiUtil;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:annual-result-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileAnalyzationResultQueryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired JobRepository jobs;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @Autowired SpecProfileAnnualSalaryResultSaveService saveService;
    @MockitoBean OpenApiUtil openApiUtil;
    Member member;
    SpecProfile profile;
    Job job;

    @BeforeEach
    void prepare() {
        member = members.saveAndFlush(new Member("result-query@example.com"));
        profile = profiles.saveAndFlush(SpecProfile.builder().member(member)
                .birthYear((short) 2002).residenceRegion(ResidentialArea.SEOUL).build());
        job = jobs.saveAndFlush(Job.builder().code("1332").name("개발자").mainCategoryName("연구직")
                .subCategoryCode("133").subCategoryName("개발직").build());
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM spec_profile_analyzation_run");
        jdbc.update("DELETE FROM spec_profile");
        jdbc.update("DELETE FROM job");
        jdbc.update("DELETE FROM member");
    }

    @Test
    @DisplayName("본인의 지정 실행 결과는 저장된 모든 통계와 과거 스냅샷을 반환하며 최신 실행이나 현재 스펙으로 대체하지 않는다")
    void 완료결과조회_통계와스냅샷전체반환_성공() throws Exception {
        // given
        SpecProfileAnnualSalaryAnalyzationResDto report = report(true);
        Long runId = saveCompletedReport(report);
        saveRun(SpecProfileAnalyzationStatus.QUEUED, "{");
        jdbc.update("UPDATE spec_profile SET birth_year=1990, residence_region='BUSAN' WHERE spec_profile_id=?", profile.getId());
        jdbc.update("UPDATE job SET name='변경된 직무명', sub_category_name='변경된 분류명' WHERE job_id=?", job.getId());
        // when
        String body = mvc.perform(get(resultUrl(runId)).header("Authorization", accessToken(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("연봉 분석 실행 상태 및 결과 조회 성공"))
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.specProfileId").value(profile.getId()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        // then
        JsonNode data = jsonMapper.readTree(body).get("data");
        assertThat(data.get("result")).isEqualTo(jsonMapper.readTree(jsonMapper.writeValueAsString(report)));
        assertThat(data.has("steps")).isFalse();
        assertThat(data.has("events")).isFalse();
        assertThat(data.get("result").get("salaryResults").get(0).has("careerLabel")).isFalse();
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
    }

    @Test
    @DisplayName("최빈값과 선택 스펙 목록이 없으면 필드를 생략하지 않고 빈 배열로 반환한다")
    void 빈최빈값과선택스펙_빈배열반환_성공() throws Exception {
        // given
        Long runId = saveRun(SpecProfileAnalyzationStatus.IN_PROGRESS,
                jsonMapper.writeValueAsString(SpecProfileAnalyzationSnapshot.builder().specProfileId(profile.getId()).build()));
        assertThat(saveService.saveAndComplete(runId, report(false))).isTrue();
        // when / then
        mvc.perform(get(resultUrl(runId)).header("Authorization", accessToken(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.statistics.annualSalaryModesKrw").isEmpty())
                .andExpect(jsonPath("$.data.result.statistics.modeFrequency").value(1))
                .andExpect(jsonPath("$.data.result.appliedSpecs.academicAbilities").isEmpty())
                .andExpect(jsonPath("$.data.result.appliedSpecs.certificates").isEmpty())
                .andExpect(jsonPath("$.data.result.appliedSpecs.languageExamScores").isEmpty())
                .andExpect(jsonPath("$.data.result.appliedSpecs.experiences").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"QUEUED", "IN_PROGRESS", "CANCELED", "FAILED"})
    @DisplayName("미완료 실행은 스냅샷을 읽거나 분석을 재실행하지 않고 상태와 명시적인 null 결과를 반환한다")
    void 미완료상태조회_null결과반환_성공(SpecProfileAnalyzationStatus state) throws Exception {
        // given
        Long runId = saveRun(state, "{");
        // when
        String body = mvc.perform(get(resultUrl(runId)).header("Authorization", accessToken(member.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(state.name()))
                .andReturn().getResponse().getContentAsString();
        // then
        JsonNode data = jsonMapper.readTree(body).get("data");
        assertThat(data.has("result")).isTrue();
        assertThat(data.get("result").isNull()).isTrue();
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(state);
    }

    @Test
    @DisplayName("타인 소유 실행은 스냅샷을 읽기 전에 미존재 실행과 동일한 404로 거절한다")
    void 타인실행조회_실패_소유권없음() throws Exception {
        // given
        Long runId = saveRun(SpecProfileAnalyzationStatus.COMPLETED, "{");
        Member other = members.saveAndFlush(new Member("other-result-query@example.com"));
        // when / then
        mvc.perform(get(resultUrl(runId)).header("Authorization", accessToken(other.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 실행은 404를 반환한다")
    void 미존재실행조회_실패() throws Exception {
        // given
        Long runId = saveRun(SpecProfileAnalyzationStatus.QUEUED, "{}");
        runs.deleteById(runId);
        // when / then
        mvc.perform(get(resultUrl(runId)).header("Authorization", accessToken(member.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없는 결과 조회는 401로 거절한다")
    void 미인증실행조회_실패() throws Exception {
        // given
        Long runId = saveRun(SpecProfileAnalyzationStatus.QUEUED, "{}");
        // when / then
        mvc.perform(get(resultUrl(runId))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("숫자가 아닌 실행 ID는 400으로 거절한다")
    void 잘못된실행ID_실패() throws Exception {
        // given / when / then
        mvc.perform(get("/api/spec-profile/analyzation/runs/invalid").header("Authorization", accessToken(member.getId())))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null"})
    @DisplayName("완료 실행의 손상되거나 null인 스냅샷은 오류로 반환하고 실행 상태를 변경하지 않는다")
    void 손상스냅샷조회_실패(String snapshotJson) throws Exception {
        // given
        Long runId = saveRun(SpecProfileAnalyzationStatus.IN_PROGRESS, snapshotJson);
        assertThat(saveService.saveAndComplete(runId, report(true))).isTrue();
        // when / then
        mvc.perform(get(resultUrl(runId)).header("Authorization", accessToken(member.getId())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INVALID_SNAPSHOT"));
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_RESULTS", "MISSING_YEAR", "MISSING_STATISTICS"})
    @DisplayName("완료 상태여도 연차나 통계가 누락되면 재분석이 필요한 오류를 반환한다")
    void 불완전저장결과조회_실패(String missing) throws Exception {
        // given
        Long runId = saveCompletedReport(report(true));
        if (missing.equals("NO_RESULTS")) {
            jdbc.update("DELETE FROM spec_profile_analyzation_result WHERE spec_profile_analyzation_run_id=?", runId);
        } else if (missing.equals("MISSING_YEAR")) {
            jdbc.update("DELETE FROM spec_profile_analyzation_result WHERE spec_profile_analyzation_run_id=? AND annual=3", runId);
        } else {
            jdbc.update("UPDATE spec_profile_analyzation_result SET sample_count=NULL WHERE spec_profile_analyzation_run_id=?", runId);
        }
        // when / then
        mvc.perform(get(resultUrl(runId)).header("Authorization", accessToken(member.getId())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INCOMPLETE_SAVED_RESULT"));
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
    }

    private Long saveCompletedReport(SpecProfileAnnualSalaryAnalyzationResDto report) {
        Long runId = saveRun(SpecProfileAnalyzationStatus.IN_PROGRESS, jsonMapper.writeValueAsString(snapshot()));
        assertThat(saveService.saveAndComplete(runId, report)).isTrue();
        return runId;
    }

    private Long saveRun(SpecProfileAnalyzationStatus state, String snapshotJson) {
        return runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(profile).job(job).createdAt(LocalDateTime.now())
                .status(state).canceledAt(state == SpecProfileAnalyzationStatus.CANCELED ? LocalDateTime.now() : null)
                .specProfileSnapshotJson(snapshotJson).build()).getId();
    }

    private String resultUrl(Long runId) {
        return "/api/spec-profile/analyzation/runs/" + runId;
    }

    private String accessToken(Long memberId) {
        return "Bearer " + jwt.createAccessToken(memberId);
    }

    private SpecProfileAnalyzationSnapshot snapshot() {
        return SpecProfileAnalyzationSnapshot.builder().specProfileId(profile.getId()).birthYear(2002)
                .residenceRegion(ResidentialArea.SEOUL).jobId(job.getId()).jobName("분석 당시 개발자")
                .academicAbilities(List.of(AcademicAbilityResDto.builder().academicAbilityId(10L)
                        .type(AcademicAbilityType.UNIVERSITY).status(AcademicAbilityStatus.GRADUATED).location(ResidentialArea.SEOUL)
                        .universityGrade(new BigDecimal("3.86")).universityStandardGrade(new BigDecimal("4.5"))
                        .graduatedYearMonth(YearMonth.of(2025, 2)).majorAffiliation(MajorMainField.ENGINEERING)
                        .majorId(17788L).majorName("소프트웨어학").build()))
                .certificates(List.of(CertificateResDto.builder().specProfileCertificateId(20L).certificateId(710L)
                        .certificateName("SQLD").acquiredYearMonth(YearMonth.of(2026, 11)).build()))
                .languageExamScores(List.of(LanguageExamScoreResDto.builder().languageExamScoreId(30L).languageExamId(1L)
                        .languageExamName("TOEIC").scoreNumeric(new BigDecimal("865"))
                        .acquiredYearMonth(YearMonth.of(2024, 8)).expiresYearMonth(YearMonth.of(2026, 8)).build()))
                .experiences(List.of(ExperienceResDto.builder().experienceId(40L).type(com.lifelog.domain.specprofile.enums.ExperienceType.PROJECT)
                        .title("분석 당시 프로젝트").startedYearMonth(YearMonth.of(2025, 1)).endedYearMonth(YearMonth.of(2025, 6))
                        .description("서버 개발").awarded(true).organizerName("기관").awardGrade("최우수").build())).build();
    }

    private SpecProfileAnnualSalaryAnalyzationResDto report(boolean hasModes) {
        SpecProfileAnalyzationSnapshot snapshot = snapshot();
        return SpecProfileAnnualSalaryAnalyzationResDto.builder().summary("저장된 요약")
                .salaryResults(List.of(
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(5, 50000000L, 54500000L, 60000000L, null),
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(0, 34000000L, 36000000L, 38000000L, new BigDecimal("28.0")),
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(3, 41000000L, 44500000L, 48000000L, null))
                        .stream().sorted(java.util.Comparator.comparingInt(SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult::annual)).toList())
                .comparisonGroup(new SpecProfileAnnualSalaryAnalyzationResDto.ComparisonGroup("133", "저장된 개발직", 120,
                        List.of(2021, 2022, 2024), "저장된 비교 집단 설명"))
                .appliedSpecs(new SpecProfileAnnualSalaryAnalyzationResDto.AppliedSpecs(snapshot.birthYear(), snapshot.residenceRegion(),
                        snapshot.jobId(), snapshot.jobName(), snapshot.academicAbilities(), snapshot.certificates(), snapshot.languageExamScores(), snapshot.experiences()))
                .statistics(KeepSalaryMatchedSampleStatistics.builder().sampleCount(120)
                        .annualSalaryMinKrw(new BigDecimal("20000000.25")).annualSalaryMaxKrw(new BigDecimal("60000000.75"))
                        .annualSalaryMeanKrw(new BigDecimal("36000000.38"))
                        .annualSalaryModesKrw(hasModes ? List.of(new BigDecimal("35000000.25"), new BigDecimal("40000000.75")) : List.of())
                        .modeFrequency(hasModes ? 8 : 1).annualSalaryP25Krw(new BigDecimal("30000000.13"))
                        .annualSalaryMedianKrw(new BigDecimal("35000000.25")).annualSalaryP75Krw(new BigDecimal("42000000.63")).build())
                .recommendation("저장된 보완점").salaryNotice("저장된 분석 기준 안내").build();
    }
}
