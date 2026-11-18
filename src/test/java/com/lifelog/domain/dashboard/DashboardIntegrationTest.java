package com.lifelog.domain.dashboard;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.dashboard.dto.DashboardResDto;
import com.lifelog.domain.dashboard.service.DashboardService;
import com.lifelog.domain.job.entity.DesiredJob;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.jobposting.entity.*;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.service.SpecProfileAnnualSalaryResultSaveService;
import com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationCacheRegistry;
import com.lifelog.global.infra.openai.OpenApiUtil;
import com.lifelog.global.security.JwtProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @Autowired AcademicAbilityRepository academics;
    @Autowired JobRepository jobs;
    @Autowired DesiredJobRepository desiredJobs;
    @Autowired CompanyRepository companies;
    @Autowired JobPostingRepository postings;
    @Autowired SpecProfileAnnualSalaryResultSaveService resultSave;
    @Autowired DashboardService dashboard;
    @Autowired SpecProfileSimulationCacheRegistry cache;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean OpenApiUtil openApiUtil;
    @Value("${jwt.secret}") String jwtSecret;
    Member member;
    SpecProfile current;
    Job job;
    Company company;
    LocalDate today;
    final LocalDateTime requestedAt = LocalDateTime.of(2026, 11, 18, 9, 30);

    @BeforeEach
    void 준비() {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        member = members.saveAndFlush(new Member(UUID.randomUUID() + "@dashboard.test"));
        current = profiles.saveAndFlush(SpecProfile.builder().member(member).birthYear((short) 1999).build());
        job = jobs.saveAndFlush(Job.builder().code("1332").name("백엔드 개발자").mainCategoryName("연구직")
                .subCategoryCode("133").subCategoryName("개발직").build());
        desiredJobs.saveAndFlush(DesiredJob.builder().member(member).job(job).build());
        academics.saveAndFlush(AcademicAbility.builder().specProfile(current).type(AcademicAbilityType.UNIVERSITY)
                .status(AcademicAbilityStatus.GRADUATED).location(ResidentialArea.SEOUL).universityGrade(new BigDecimal("3.8"))
                .universityStandardGrade(new BigDecimal("4.5")).build());
        company = companies.saveAndFlush(Company.builder().name("대시보드 테스트 기업").build());
    }

    @AfterEach
    void 정리() {
        cache.evict(member.getId());
        for (String table : List.of("job_posting_bookmark", "job_posting_job", "job_posting_requirement", "job_posting_pay", "job_posting",
                "company", "spec_profile_analyzation_run", "academic_ability_major", "academic_ability", "spec_profile_certificate",
                "language_exam_score", "experience", "spec_profile", "desired_job", "job", "member")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("저장 초봉·스냅샷과 기존 공고 상위 3개를 반환하며 DB·캐시·기존 응답 계약을 유지한다")
    void 대시보드조회_저장결과와기존공고재사용_성공() throws Exception {
        // given
        Long runId = saveCompleted();
        savePosting("둘째", job, 5, today.plusDays(7), "Spring Boot");
        JobPosting first = savePosting("첫째", job, 5, today.plusDays(2), " Java ", "Java", "Spring Boot");
        savePosting("셋째", job, 5, null);
        savePosting("넷째", job, 5, null);
        jdbc.update("UPDATE spec_profile SET birth_year=1990 WHERE spec_profile_id=?", current.getId());
        jdbc.update("UPDATE academic_ability SET university_grade=2.0 WHERE spec_profile_id=?", current.getId());
        jdbc.update("UPDATE job SET name='수정한 직무명' WHERE job_id=?", job.getId());
        SpecProfileSimulationContext cached = new SpecProfileSimulationContext(snapshot(), null);
        cache.store(member.getId(), cached);
        JsonNode oldJobs = json(existingJobs());
        JsonNode oldReport = json(existingReport(runId));
        Map<String, List<Map<String, Object>>> before = databaseState();
        // when
        JsonNode response = json(query().andExpect(status().isOk()));
        // then
        assertThat(response.path("status").asInt()).isEqualTo(200);
        assertThat(response.has("success")).isFalse();
        JsonNode analysis = response.path("data").path("analysis");
        assertThat(analysis.path("runId").asLong()).isEqualTo(runId);
        assertThat(LocalDateTime.parse(analysis.path("requestedAt").asString())).isEqualTo(requestedAt.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime());
        assertThat(analysis.path("result").path("expectedStartingSalary").asLong()).isEqualTo(36000000L);
        assertThat(analysis.path("result").path("topPercent").decimalValue()).isEqualByComparingTo("35.0");
        assertThat(analysis.path("result").path("salaryNotice").asString()).isEqualTo("저장된 산출 안내");
        assertThat(analysis.path("result").path("coreSpecs").get(1).path("label").asString()).isEqualTo("학점 3.8 / 4.5");
        assertThat(analysis.path("result").path("coreSpecs").get(3).path("label").asString()).isEqualTo("희망 직무 백엔드 개발자");
        JsonNode recommended = response.path("data").path("recommendedJobPostings");
        assertThat(recommended.path("totalCount").asLong()).isEqualTo(4);
        assertThat(recommended.path("personalized").asBoolean()).isTrue();
        JsonNode cards = recommended.path("jobPostings");
        assertThat(cards.size()).isEqualTo(3);
        assertThat(cards.get(0).path("jobPostingId").asLong()).isEqualTo(first.getId());
        assertThat(cards.get(0).path("skillTags")).isEqualTo(jsonMapper.readTree("[\"Java\",\"Spring Boot\"]"));
        assertThat(cards.get(0).has("expectedSalaryMin")).isTrue();
        assertThat(cards.get(0).path("expectedSalaryMin").isNull()).isTrue();
        assertThat(cards.get(0).path("dDay").asInt()).isEqualTo(2);
        assertThat(cards.get(2).path("dDay").isNull()).isTrue();
        assertThat(cards.get(2).path("skillTags").size()).isZero();
        for (int index = 0; index < cards.size(); index++) {
            assertThat(cards.get(index).path("jobPostingId")).isEqualTo(oldJobs.path("data").path("jobPostings").get(index).path("jobPostingId"));
        }
        assertThat(json(existingJobs())).isEqualTo(oldJobs);
        assertThat(json(existingReport(runId))).isEqualTo(oldReport);
        assertThat(databaseState()).isEqualTo(before);
        assertThat(cache.find(member.getId()).orElseThrow()).isSameAs(cached);
        verifyNoInteractions(openApiUtil);
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"QUEUED", "IN_PROGRESS", "FAILED", "CANCELED"})
    @DisplayName("최신 미완료 실행은 과거 완료 결과를 대신 보여주거나 잘못된 스냅샷을 해석하지 않는다")
    void 최신미완료_과거완료대체없음_성공(SpecProfileAnalyzationStatus state) throws Exception {
        // given
        saveCompleted();
        Long latest = saveRun(current, state, requestedAt.plusSeconds(1), "{");
        // when / then
        JsonNode analysis = json(query().andExpect(status().isOk())).path("data").path("analysis");
        assertThat(analysis.path("runId").asLong()).isEqualTo(latest);
        assertThat(analysis.path("status").asString()).isEqualTo(state.name());
        assertThat(analysis.has("result")).isTrue();
        assertThat(analysis.path("result").isNull()).isTrue();
        verifyNoInteractions(openApiUtil);
    }

    @Test
    @DisplayName("타인·TARGET·삭제 실행을 제외하고 요청 시각이 같으면 큰 실행 ID를 선택한다")
    void 최신실행선택_소유권유형삭제동률_성공() throws Exception {
        // given
        saveRun(current, SpecProfileAnalyzationStatus.QUEUED, requestedAt, "{");
        Long expected = saveRun(current, SpecProfileAnalyzationStatus.IN_PROGRESS, requestedAt, "{");
        Long deleted = saveRun(current, SpecProfileAnalyzationStatus.QUEUED, requestedAt.plusDays(1), "{");
        jdbc.update("UPDATE spec_profile_analyzation_run SET delete_at=? WHERE run_id=?", requestedAt, deleted);
        SpecProfile target = profiles.saveAndFlush(SpecProfile.builder().member(member).type(SpecProfileType.TARGET).build());
        saveRun(target, SpecProfileAnalyzationStatus.QUEUED, requestedAt.plusDays(2), "{");
        Member other = members.saveAndFlush(new Member(UUID.randomUUID() + "@other-dashboard.test"));
        SpecProfile otherProfile = profiles.saveAndFlush(SpecProfile.builder().member(other).build());
        saveRun(otherProfile, SpecProfileAnalyzationStatus.QUEUED, requestedAt.plusDays(3), "{");
        // when / then
        query().andExpect(status().isOk()).andExpect(jsonPath("$.data.analysis.runId").value(expected));
    }

    @Test
    @DisplayName("CURRENT나 분석이 없더라도 생성하지 않으며 빈 목록과 명시적인 null 분석을 반환한다")
    void 분석과현재스펙없음_빈대시보드_성공() throws Exception {
        // given
        academics.deleteAll();
        profiles.deleteAll();
        desiredJobs.deleteAll();
        // when / then
        String body = query().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendedJobPostings.personalized").value(false))
                .andExpect(jsonPath("$.data.recommendedJobPostings.totalCount").value(0))
                .andExpect(jsonPath("$.data.recommendedJobPostings.jobPostings").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"analysis\":null");
        assertThat(profiles.count()).isZero();
        assertThat(runs.count()).isZero();
    }

    @Test
    @DisplayName("현재 희망 직무와 인정 학력·모집 상태를 적용하며 오늘 마감과 상시·채용 시 마감을 반환한다")
    void 공고추천_현재조건과마감유형_성공() throws Exception {
        // given
        savePosting("오늘", job, 5, today);
        savePosting("상시", job, 5, null);
        JobPosting until = savePosting("채용 시", job, 5, null);
        jdbc.update("UPDATE job_posting SET deadline_type='UNTIL_FILLED' WHERE job_posting_id=?", until.getId());
        savePosting("고학력", job, 7, today.plusDays(1));
        savePosting("지난 공고", job, 5, today.minusDays(1));
        JobPosting closed = savePosting("마감", job, 5, today.plusDays(1));
        jdbc.update("UPDATE job_posting SET status='CLOSE' WHERE job_posting_id=?", closed.getId());
        Job otherJob = jobs.saveAndFlush(Job.builder().code("2311").name("다른 직무").mainCategoryName("다른 분류").subCategoryCode("231").build());
        savePosting("다른 직무", otherJob, 5, today.plusDays(1));
        // when
        JsonNode result = json(query().andExpect(status().isOk())).path("data").path("recommendedJobPostings");
        // then
        assertThat(result.path("totalCount").asLong()).isEqualTo(3);
        assertThat(result.path("jobPostings").get(0).path("dDay").asInt()).isZero();
        assertThat(result.path("jobPostings").get(1).path("deadlineType").asString()).isEqualTo("UNTIL_FILLED");
        assertThat(result.path("jobPostings").get(2).path("deadlineType").asString()).isEqualTo("ALWAYS");
        desiredJobs.deleteAll();
        assertThat(json(query().andExpect(status().isOk())).path("data").path("recommendedJobPostings").path("totalCount").asLong()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESULT", "YEAR", "STATISTICS", "SNAPSHOT"})
    @DisplayName("손상된 완료 결과는 기존 오류로 반환하고 재분석이나 상태 변경을 실행하지 않는다")
    void 손상저장정보_기존오류유지_실패(String missing) throws Exception {
        // given
        Long id = saveCompleted();
        switch (missing) {
            case "RESULT" -> jdbc.update("DELETE FROM spec_profile_analyzation_result WHERE spec_profile_analyzation_run_id=?", id);
            case "YEAR" -> jdbc.update("DELETE FROM spec_profile_analyzation_result WHERE spec_profile_analyzation_run_id=? AND annual=3", id);
            case "STATISTICS" -> jdbc.update("UPDATE spec_profile_analyzation_result SET sample_count=null WHERE spec_profile_analyzation_run_id=?", id);
            case "SNAPSHOT" -> jdbc.update("UPDATE spec_profile_analyzation_run SET spec_profile_snapshot_json='{' WHERE run_id=?", id);
            default -> throw new IllegalArgumentException(missing);
        }
        Map<String, List<Map<String, Object>>> before = databaseState();
        // when / then
        query().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code")
                .value(missing.equals("SNAPSHOT") ? "INVALID_SNAPSHOT" : "INCOMPLETE_SAVED_RESULT"));
        assertThat(databaseState()).isEqualTo(before);
        verifyNoInteractions(openApiUtil);
    }

    @Test
    @DisplayName("토큰 누락·잘못된 토큰·만료 토큰은 기존 AUTH_401 응답을 사용한다")
    void 인증정보없음_기존인증오류_실패() throws Exception {
        // given / when / then
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401"));
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401"));
        String expired = new JwtProvider(jwtSecret, -60000).createAccessToken(member.getId());
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @Test
    @DisplayName("공고 요건 수가 늘어도 보충 조회는 항목마다 쿼리를 실행하지 않는다")
    void 공고기술요건_일괄조회_성공() {
        // given
        JobPosting posting = savePosting("공고", job, 5, null, "Java");
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        dashboard.getDashboard(member.getId());
        long small = factory.getStatistics().getPrepareStatementCount();
        JobPosting stored = postings.findAllWithRequirementsByIdIn(List.of(posting.getId())).getFirst();
        for (int index = 0; index < 10; index++) stored.addRequirement(requirement("기술 " + index, index + 1));
        postings.saveAndFlush(stored);
        factory.getStatistics().clear();
        // when
        DashboardResDto result = dashboard.getDashboard(member.getId());
        // then
        assertThat(result.data().recommendedJobPostings().jobPostings().getFirst().skillTags()).hasSize(11);
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(small);
    }

    @Test
    @DisplayName("Swagger에 전용 성공 응답과 기존 오류 응답이 문서화된다")
    void 스웨거스키마_응답계약_성공() throws Exception {
        // given / when
        JsonNode document = json(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        // then
        JsonNode operation = document.path("paths").path("/api/dashboard").path("get");
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("401")).isTrue();
        assertThat(operation.path("responses").has("500")).isTrue();
        assertThat(operation.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asString())
                .endsWith("/DashboardResDto");
        assertThat(operation.path("description").asString()).contains("### 7. Response Body");
    }

    protected Long saveCompleted() {
        Long id = saveRun(current, SpecProfileAnalyzationStatus.IN_PROGRESS, requestedAt, jsonMapper.writeValueAsString(snapshot()));
        assertThat(resultSave.saveAndComplete(id, report())).isTrue();
        return id;
    }

    protected Long saveRun(SpecProfile profile, SpecProfileAnalyzationStatus state, LocalDateTime time, String snapshotJson) {
        return runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(profile).job(job).status(state)
                .createdAt(time).canceledAt(state == SpecProfileAnalyzationStatus.CANCELED ? time : null)
                .specProfileSnapshotJson(snapshotJson).build()).getId();
    }

    protected JobPosting savePosting(String title, Job linkedJob, int education, LocalDate close, String... skills) {
        JobPosting posting = JobPosting.builder().title(title).company(company).employmentType(EmploymentType.FULL_TIME)
                .careerType(CareerType.ANY).regionCode("11680").regionName("서울 강남구").workplaceAddress("서울 강남구")
                .educationLevel((short) education).deadlineType(close == null ? DeadlineType.ALWAYS : DeadlineType.FIXED)
                .closesAt(close == null ? null : close.atTime(23, 59, 59)).status(JobPostingStatus.OPEN)
                .source("dashboard-test").externalId(UUID.randomUUID().toString()).build();
        posting.replaceJob(linkedJob);
        for (int index = 0; index < skills.length; index++) posting.addRequirement(requirement(skills[index], index));
        return postings.saveAndFlush(posting);
    }

    private JobPostingRequirement requirement(String skill, int order) {
        return JobPostingRequirement.builder().type(RequirementType.SKILL).priority(RequirementPriority.REQUIRED)
                .content(skill).sortOrder((short) order).build();
    }

    private SpecProfileAnalyzationSnapshot snapshot() {
        return SpecProfileAnalyzationSnapshot.builder().specProfileId(current.getId()).jobId(job.getId()).jobName("백엔드 개발자")
                .academicAbilities(List.of(AcademicAbilityResDto.builder().type(AcademicAbilityType.UNIVERSITY).majorName("컴퓨터공학과")
                        .universityGrade(new BigDecimal("3.8")).universityStandardGrade(new BigDecimal("4.5")).build()))
                .certificates(List.of(CertificateResDto.builder().certificateName("SQLD").build()))
                .languageExamScores(List.of()).experiences(List.of()).build();
    }

    private SpecProfileAnnualSalaryAnalyzationResDto report() {
        SpecProfileAnalyzationSnapshot snapshot = snapshot();
        return SpecProfileAnnualSalaryAnalyzationResDto.builder().summary("저장된 요약")
                .salaryResults(List.of(new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(0, 34000000L, 36000000L, 38000000L, new BigDecimal("35.0")),
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(3, 41000000L, 45000000L, 48000000L, null),
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(5, 50000000L, 55000000L, 60000000L, null)))
                .comparisonGroup(new SpecProfileAnnualSalaryAnalyzationResDto.ComparisonGroup("133", "개발직", 20, List.of(2024), "비교 집단"))
                .appliedSpecs(new SpecProfileAnnualSalaryAnalyzationResDto.AppliedSpecs(null, null, job.getId(), snapshot.jobName(),
                        snapshot.academicAbilities(), snapshot.certificates(), List.of(), List.of()))
                .statistics(KeepSalaryMatchedSampleStatistics.builder().sampleCount(20).modeFrequency(1).annualSalaryModesKrw(List.of())
                        .annualSalaryMinKrw(new BigDecimal("20000000")).annualSalaryMaxKrw(new BigDecimal("60000000"))
                        .annualSalaryMeanKrw(new BigDecimal("40000000")).annualSalaryP25Krw(new BigDecimal("30000000"))
                        .annualSalaryMedianKrw(new BigDecimal("35000000")).annualSalaryP75Krw(new BigDecimal("45000000")).build())
                .recommendation("저장된 보완점").salaryNotice("저장된 산출 안내").build();
    }

    private ResultActions query() throws Exception {
        return mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())));
    }

    private ResultActions existingJobs() throws Exception {
        return mvc.perform(get("/api/job-postings").param("size", "3").header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.code").value("COMMON_200"));
    }

    private ResultActions existingReport(Long id) throws Exception {
        return mvc.perform(get("/api/spec-profile/analyzation/runs/" + id).header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.code").value("COMMON_200"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Map<String, List<Map<String, Object>>> databaseState() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("spec_profile", "spec_profile_analyzation_run", "spec_profile_analyzation_result",
                "spec_profile_analyzation_event_log", "academic_ability", "desired_job", "job_posting", "job_posting_bookmark")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return result;
    }
}
