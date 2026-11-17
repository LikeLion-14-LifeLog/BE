package com.lifelog.domain.specprofile;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.enums.CertificationMainCategory;
import com.lifelog.domain.certification.enums.CertificationType;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.job.entity.DesiredJob;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.major.service.MajorCsvReader;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationMapper;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnnualSalaryAnalyzationMapper;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.domain.specprofile.service.SpecProfileQueryService;
import com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationCacheRegistry;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenApiErrorCode;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import com.lifelog.global.infra.openai.OpenApiUtil;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:target-save;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false",
        "keep.salary.csv-path=classpath:data/keep_salary_preparation.csv"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileTargetSaveIntegrationTest {
    private static final String URL = "/api/spec-profile/target";
    private static final List<String> ANALYSIS_TABLES = List.of("spec_profile_analyzation_run", "spec_profile_analyzation_event_log",
            "spec_profile_analyzation_result", "spec_profile_analyzation_result_salary_mode", "spec_profile_analyzation_result_observation_year");

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired JobRepository jobs;
    @Autowired DesiredJobRepository desiredJobs;
    @Autowired MajorRepository majors;
    @Autowired MajorCsvReader majorReader;
    @Autowired AcademicAbilityRepository academics;
    @Autowired AcademicAbilityMajorRepository academicMajors;
    @Autowired CertificationRepository certifications;
    @Autowired SpecProfileCertificateRepository certificates;
    @Autowired LanguageExamRepository exams;
    @Autowired LanguageExamScoreRepository scores;
    @Autowired ExperienceRepository experiences;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @Autowired SpecProfileAnalyzationResultRepository results;
    @Autowired SpecProfileAnnualSalaryAnalyzationMapper resultMapper;
    @Autowired SpecProfileAnalyzationMapper snapshotMapper;
    @Autowired SpecProfileQueryService queryService;
    @Autowired SpecProfileSimulationCacheRegistry cacheRegistry;
    @MockitoBean OpenApiUtil openApiUtil;

    final List<JsonNode> inputs = new CopyOnWriteArrayList<>();
    final AtomicInteger simulationCalls = new AtomicInteger();
    final AtomicInteger recommendationCalls = new AtomicInteger();
    final AtomicInteger evaluationCalls = new AtomicInteger();
    Member member;
    SpecProfile profile;
    Job job;
    Major major;
    Certification targetCertificate;
    Supplier<SpecProfileTargetRecommendationAiResDto> recommendation;
    boolean failRecommendation;
    boolean rejectEvaluation;
    boolean changeCurrentDuringEvaluation;
    CyclicBarrier evaluationBarrier;

    @BeforeEach
    void 준비() {
        member = members.saveAndFlush(new Member(UUID.randomUUID() + "@target.test"));
        profile = profiles.saveAndFlush(SpecProfile.builder().member(member).birthYear((short) 1999)
                .residenceRegion(ResidentialArea.SEOUL).build());
        job = jobs.saveAndFlush(Job.builder().code("1332").name("개발자").mainCategoryName("연구직")
                .subCategoryCode("133").subCategoryName("개발직").build());
        desiredJobs.saveAndFlush(DesiredJob.builder().member(member).job(job).build());
        MajorCsvRow row = majorReader.read().rows().stream().filter(value -> value.code().equals("U04080100061"))
                .findFirst().orElseThrow();
        major = majors.saveAndFlush(Major.builder().code(row.code()).name(row.name()).degreeProcess(row.degreeProcess())
                .mainField(row.mainField()).middleField(row.middleField()).subField(row.subField()).build());
        AcademicAbility academic = academics.saveAndFlush(AcademicAbility.builder().specProfile(profile)
                .type(AcademicAbilityType.UNIVERSITY).status(AcademicAbilityStatus.GRADUATED)
                .location(ResidentialArea.SEOUL).universityGrade(new BigDecimal("3.75"))
                .universityStandardGrade(new BigDecimal("4.5")).graduatedAt(LocalDate.of(2024, 2, 1)).build());
        academicMajors.saveAndFlush(AcademicAbilityMajor.builder().academicAbility(academic).major(major).build());
        Certification owned = certifications.saveAndFlush(Certification.builder().type(CertificationType.PRIVATE)
                .mainCategory(CertificationMainCategory.COMMON).name("기존 자격증").code("TGT1").build());
        targetCertificate = certifications.saveAndFlush(Certification.builder().type(CertificationType.PRIVATE)
                .mainCategory(CertificationMainCategory.COMMON).name("목표 자격증").code("TGT2").build());
        certificates.saveAndFlush(SpecProfileCertificate.builder().specProfile(profile).certificate(owned)
                .acquiredAt(LocalDate.of(2024, 1, 1)).build());
        LanguageExam toeic = exams.saveAndFlush(LanguageExam.builder().id(1L).name("TOEIC")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.NUMERIC).build());
        exams.saveAndFlush(LanguageExam.builder().id(7L).name("OPIc")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.GRADE).build());
        scores.saveAndFlush(LanguageExamScore.builder().specProfile(profile).languageExam(toeic)
                .scoreNumeric(new BigDecimal("875")).acquiredAt(LocalDate.of(2024, 8, 1)).build());
        experiences.saveAndFlush(Experience.builder().specProfile(profile).type(ExperienceType.PROJECT)
                .title("현재 프로젝트").startedAt(LocalDate.of(2024, 1, 1)).build());
        inputs.clear();
        simulationCalls.set(0);
        recommendationCalls.set(0);
        evaluationCalls.set(0);
        failRecommendation = rejectEvaluation = changeCurrentDuringEvaluation = false;
        evaluationBarrier = null;
        recommendation = this::validRecommendations;
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            OpenApiPrompt prompt = invocation.getArgument(0);
            JsonNode input = jsonMapper.readTree(prompt.inputJson());
            inputs.add(input);
            Class<?> responseType = invocation.getArgument(1);
            if (responseType == SpecProfileSimulationAiResDto.class) {
                simulationCalls.incrementAndGet();
                JsonNode spec = input.path("snapshot");
                boolean user = spec.path("academicAbilities").size() > 1;
                long salary = user ? 41000000L : spec.path("certificates").size() > 1 ? 39000000L
                        : spec.path("experiences").size() > 1 ? 37000000L : 36000000L;
                return simulationResponse(salary, user ? "약 6개월" : "약 2개월");
            }
            if (responseType == SpecProfileTargetRecommendationAiResDto.class) {
                recommendationCalls.incrementAndGet();
                if (failRecommendation) throw new CustomException(OpenApiErrorCode.OPEN_API_QUOTA_EXCEEDED);
                assertThat(prompt.schemaName()).isEqualTo("target_spec_recommendation_v1");
                return recommendation.get();
            }
            if (responseType == SpecProfileTargetEvaluationAiResDto.class) {
                evaluationCalls.incrementAndGet();
                if (changeCurrentDuringEvaluation) jdbc.update("UPDATE spec_profile SET birth_year=1998 WHERE spec_profile_id=?", profile.getId());
                if (evaluationBarrier != null) evaluationBarrier.await(15, TimeUnit.SECONDS);
                return SpecProfileTargetEvaluationAiResDto.builder().evaluations(List.of(
                        new SpecProfileTargetEvaluationAiResDto.Evaluation(2, !rejectEvaluation, "기간·비용 부담을 줄이는 장점과 낮은 예상 연봉을 함께 고려"),
                        new SpecProfileTargetEvaluationAiResDto.Evaluation(3, !rejectEvaluation, "직무 포트폴리오 활용도와 짧은 준비 기간을 고려"))).build();
            }
            throw new AssertionError("예상하지 않은 AI 요청 " + responseType);
        }).when(openApiUtil).request(any(OpenApiPrompt.class), any());
    }

    @AfterEach
    void 정리() {
        cacheRegistry.evict(member.getId());
        for (String table : List.of("spec_profile_analyzation_result_salary_mode", "spec_profile_analyzation_result_observation_year",
                "spec_profile_analyzation_result", "spec_profile_analyzation_event_log", "spec_profile_analyzation_run",
                "academic_ability_major", "academic_ability", "spec_profile_certificate", "language_exam_score", "experience",
                "desired_job", "spec_profile", "major", "certificate", "language_exam", "job", "member")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("사용자 TARGET 1개만 저장하고 세 선택지의 독립 분석과 준비 정보를 반환한다")
    void 사용자목표만저장_세슬롯응답_성공() throws Exception {
        // given
        SpecProfileResDto currentBefore = queryService.getCurrentProfile(member.getId());
        Map<String, List<Map<String, Object>>> analysisBefore = analysisState();
        // when
        JsonNode data = data(save(fullRequest()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options[0].preparationPeriod").value("약 6개월"))
                .andExpect(jsonPath("$.data.options[1].preparationPeriod").value("약 2개월")));
        // then
        long targetId = data.path("selectedTargetSpecProfileId").asLong();
        assertThat(data.path("baseExpectedStartingSalary").asLong()).isEqualTo(36000000L);
        assertThat(data.path("companyGroupScale").asString()).isEqualTo("LARGE");
        assertThat(data.path("currency").asString()).isEqualTo("KRW");
        assertThat(data.path("options").size()).isEqualTo(3);
        JsonNode user = data.path("options").get(0);
        assertThat(user.path("targetSpecProfileId").asLong()).isEqualTo(targetId);
        assertThat(user.path("source").asString()).isEqualTo("USER_INPUT");
        assertThat(user.path("selected").asBoolean()).isTrue();
        assertThat(user.path("expectedStartingSalary").asLong()).isEqualTo(41000000L);
        assertThat(user.path("salaryIncreaseAmount").asLong()).isEqualTo(5000000L);
        for (int index = 1; index < 3; index++) {
            JsonNode option = data.path("options").get(index);
            assertThat(option.path("slotNumber").asInt()).isEqualTo(index + 1);
            assertThat(option.has("targetSpecProfileId")).isTrue();
            assertThat(option.path("targetSpecProfileId").isNull()).isTrue();
            assertThat(option.path("selected").asBoolean()).isFalse();
            assertThat(option.path("source").asString()).isEqualTo("AI_RECOMMENDATION");
            assertThat(option.path("changes").isObject()).isTrue();
            assertThat(option.path("estimatedCost").asString()).isNotBlank();
            assertThat(option.path("decisionCriteria").asString()).isNotBlank();
        }
        assertThat(data.path("options").get(1).path("expectedStartingSalary").asLong()).isEqualTo(39000000L);
        assertThat(data.path("options").get(2).path("expectedStartingSalary").asLong()).isEqualTo(37000000L);
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isEqualTo(1);
        SpecProfile target = profiles.findById(targetId).orElseThrow();
        assertThat(target.getSortOrder()).isEqualTo((short) 1);
        assertThat(target.getType()).isEqualTo(SpecProfileType.TARGET);
        assertThat(target.getTargetSpecSelected()).isTrue();
        assertThat(target.getBaseSpecProfile().getId()).isEqualTo(profile.getId());
        assertThat(academics.findAllBySpecProfileId(targetId)).hasSize(2);
        assertThat(certificates.findAllBySpecProfileId(targetId)).hasSize(2);
        assertThat(scores.findAllBySpecProfileId(targetId)).hasSize(2);
        assertThat(experiences.findAllBySpecProfileId(targetId)).hasSize(2).anySatisfy(value -> {
            assertThat(value.getTitle()).isEqualTo("사용자 인턴");
            assertThat(value.getEndedAt()).isEqualTo(LocalDate.of(2027, 6, 1));
        });
        assertThat(queryService.getCurrentProfile(member.getId())).isEqualTo(currentBefore);
        assertThat(analysisState()).isEqualTo(analysisBefore);
        assertThat(simulationCalls.get()).isEqualTo(4);
        assertThat(cacheRegistry.find(member.getId())).isEmpty();
        List<JsonNode> analysisInputs = inputs.stream().filter(value -> value.has("snapshot")).toList();
        assertThat(analysisInputs).allSatisfy(value -> assertThat(value.path("companyGroupScale").asString()).isEqualTo("LARGE"));
        assertThat(analysisInputs.get(2).path("snapshot").path("academicAbilities").size()).isEqualTo(1);
        assertThat(analysisInputs.get(3).path("snapshot").path("certificates").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 POST의 동일 조건 캐시를 재사용하고 PUT 응답은 캐시에 덮어쓰지 않는다")
    void 최근캐시재사용_사용자재분석생략_성공() throws Exception {
        // given
        simulate(fullRequest()).andExpect(status().isOk());
        SpecProfileSimulationContext cached = cacheRegistry.find(member.getId()).orElseThrow();
        simulationCalls.set(0);
        // when / then
        save(fullRequest()).andExpect(status().isOk());
        assertThat(simulationCalls.get()).isEqualTo(2);
        assertThat(cacheRegistry.find(member.getId()).orElseThrow()).isSameAs(cached);
    }

    @Test
    @DisplayName("기업 규모가 다른 캐시는 재사용하지 않고 모든 시나리오를 같은 새 규모로 분석한다")
    void 기업규모변경_캐시무효_성공() throws Exception {
        // given
        simulate(fullRequest()).andExpect(status().isOk());
        simulationCalls.set(0);
        inputs.clear();
        // when / then
        save(fullRequest().replace("LARGE", "SMALL")).andExpect(status().isOk());
        assertThat(simulationCalls.get()).isEqualTo(4);
        assertThat(inputs).allSatisfy(value -> assertThat(value.path("companyGroupScale").asString()).isEqualTo("SMALL"));
    }

    @Test
    @DisplayName("현재 하위 스펙이 바뀌면 같은 사용자 캐시라도 재분석한다")
    void 현재스펙변경_캐시무효_성공() throws Exception {
        // given
        simulate(fullRequest()).andExpect(status().isOk());
        jdbc.update("UPDATE experience SET title='변경된 현재 프로젝트' WHERE spec_profile_id=?", profile.getId());
        simulationCalls.set(0);
        // when / then
        save(fullRequest()).andExpect(status().isOk());
        assertThat(simulationCalls.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("준비 정보가 누락된 캐시는 다시 분석한다")
    void 불완전캐시_사용자재분석_성공() throws Exception {
        // given
        simulate(fullRequest()).andExpect(status().isOk());
        SpecProfileSimulationContext cached = cacheRegistry.find(member.getId()).orElseThrow();
        cacheRegistry.store(member.getId(), new SpecProfileSimulationContext(cached.base(), cached.simulated(),
                cached.companyGroupScale(), cached.baseAnalysis(), cached.simulatedAnalysis(),
                SpecProfileSimulationResDto.builder().preparationPeriod(null).estimatedCost("비용").decisionCriteria("판단").build()));
        simulationCalls.set(0);
        // when / then
        save(fullRequest()).andExpect(status().isOk());
        assertThat(simulationCalls.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("이전 장문 비용 캐시는 재사용하지 않고 세 카드에 간결한 해요체 결과를 반환한다")
    void 장문캐시_재분석후간결한결과반환_성공() throws Exception {
        // given
        simulate(fullRequest()).andExpect(status().isOk());
        SpecProfileSimulationContext cached = cacheRegistry.find(member.getId()).orElseThrow();
        ObjectNode previousResponse = (ObjectNode) jsonMapper.valueToTree(cached.response());
        previousResponse.put("estimatedCost", "참고 추정 약 0~150,000원. 기존 개발용 컴퓨터와 무료 도구를 사용하고 유료 교육 없이 진행하는 가정이며 기존 학력·자격증·어학·프로젝트 비용은 포함하지 않았다.");
        cacheRegistry.store(member.getId(), new SpecProfileSimulationContext(cached.base(), cached.simulated(),
                cached.companyGroupScale(), cached.baseAnalysis(), cached.simulatedAnalysis(),
                jsonMapper.treeToValue(previousResponse, SpecProfileSimulationResDto.class)));
        simulationCalls.set(0);
        // when
        JsonNode response = data(save(fullRequest()).andExpect(status().isOk()));
        // then
        assertThat(simulationCalls.get()).isEqualTo(4);
        assertThat(response.path("options").valueStream().toList()).hasSize(3).allSatisfy(option -> {
            assertThat(option.path("estimatedCost").asString()).isEqualTo("약 35만 원");
            assertThat(option.path("decisionCriteria").asString()).isEqualTo("실무 경험을 중시하는 채용에 적합해요.");
        });
    }

    @Test
    @DisplayName("추가 조건과 기업 규모가 없으면 현재 스펙을 1번 목표로 저장하고 같은 기준 연봉을 사용한다")
    void 추가조건없음_현재유지목표_성공() throws Exception {
        // given / when
        JsonNode response = data(save("{\"baseSpecProfileId\":" + profile.getId() + "}").andExpect(status().isOk()));
        // then
        assertThat(response.path("companyGroupScale").isNull()).isTrue();
        assertThat(response.path("options").get(0).path("title").asString()).isEqualTo("현재 스펙 유지");
        assertThat(response.path("options").get(0).path("salaryIncreaseAmount").asLong()).isZero();
        assertThat(simulationCalls.get()).isEqualTo(3);
        assertThat(inputs).allSatisfy(value -> assertThat(value.path("companyGroupScale").isNull()).isTrue());
    }

    @Test
    @DisplayName("반복 요청은 같은 TARGET ID를 갱신하고 하위 항목을 누적하지 않는다")
    void 목표덮어쓰기_행중복방지_성공() throws Exception {
        // given
        long firstId = data(save(fullRequest()).andExpect(status().isOk())).path("selectedTargetSpecProfileId").asLong();
        // when
        long secondId = data(save(fullRequest().replace("사용자 인턴", "수정한 인턴")).andExpect(status().isOk()))
                .path("selectedTargetSpecProfileId").asLong();
        // then
        assertThat(secondId).isEqualTo(firstId);
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isEqualTo(1);
        assertThat(experiences.findAllBySpecProfileId(firstId)).hasSize(2)
                .extracting(Experience::getTitle).contains("수정한 인턴").doesNotContain("사용자 인턴");
        assertThat(analysisState().values()).allSatisfy(value -> assertThat(value).isEmpty());
    }

    @Test
    @DisplayName("저장하지 않은 AI 대안의 changes를 다시 제출해 사용자 목표로 적용한다")
    void AI대안재제출_사용자목표로저장_성공() throws Exception {
        // given
        JsonNode first = data(save(fullRequest()).andExpect(status().isOk()));
        ObjectNode request = jsonMapper.createObjectNode().put("baseSpecProfileId", profile.getId()).put("companyGroupScale", "LARGE");
        request.set("changes", first.path("options").get(1).path("changes"));
        SpecProfileTargetRecommendationAiResDto normal = validRecommendations();
        recommendation = () -> SpecProfileTargetRecommendationAiResDto.builder().options(List.of(normal.options().get(1),
                new SpecProfileTargetRecommendationAiResDto.Option("코드 기여", new SpecProfileTargetChanges(null, null, null,
                        List.of(new SpecProfileTargetChanges.ExperienceChange(ExperienceType.OPEN_SOURCE, "오픈 소스 기여",
                                java.time.YearMonth.of(2027, 1), null, "코드 리뷰", false, null, null))), "협업 활용도"))).build();
        // when
        JsonNode second = data(save(request.toString()).andExpect(status().isOk()));
        // then
        assertThat(second.path("selectedTargetSpecProfileId")).isEqualTo(first.path("selectedTargetSpecProfileId"));
        long id = second.path("selectedTargetSpecProfileId").asLong();
        assertThat(experiences.findAllBySpecProfileId(id)).hasSize(1);
        assertThat(certificates.findAllBySpecProfileId(id)).hasSize(2);
    }

    @Test
    @DisplayName("미정 취득일과 100점 만점 목표를 저장하며 원본 날짜를 응답에 보존한다")
    void 미래목표_미정날짜와백점학점저장_성공() throws Exception {
        // given
        ObjectNode request = (ObjectNode) jsonMapper.readTree(fullRequest());
        ((ObjectNode) request.path("changes").path("academicAbilities").get(0)).put("universityGrade", 95).put("universityStandardGrade", 100);
        // when
        JsonNode data = data(save(request.toString()).andExpect(status().isOk()));
        // then
        long id = data.path("selectedTargetSpecProfileId").asLong();
        assertThat(academics.findAllBySpecProfileId(id)).anySatisfy(value -> assertThat(value.getUniversityGrade()).isEqualByComparingTo("95"));
        assertThat(certificates.findAllBySpecProfileId(id)).anySatisfy(value -> assertThat(value.getAcquiredAt()).isNull());
        assertThat(scores.findAllBySpecProfileId(id)).anySatisfy(value -> assertThat(value.getAcquiredAt()).isNull());
        assertThat(data.path("options").get(0).path("changes").path("experiences").get(0).path("endedYearMonth").asString()).isEqualTo("2027-06");
    }

    @Test
    @DisplayName("기존 CURRENT 분석 결과를 추천 입력에 전달하고 분석 테이블을 변경하지 않는다")
    void 기존분석조회_추천입력전달_성공() throws Exception {
        // given
        simulate(fullRequest()).andExpect(status().isOk());
        SpecProfileSimulationContext cached = cacheRegistry.find(member.getId()).orElseThrow();
        SpecProfileAnalyzationRun run = runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(profile).job(job)
                .createdAt(LocalDateTime.now()).status(SpecProfileAnalyzationStatus.COMPLETED)
                .specProfileSnapshotJson(jsonMapper.writeValueAsString(cached.base())).build());
        results.saveAllAndFlush(resultMapper.toResults(run, cached.baseAnalysis()));
        Map<String, List<Map<String, Object>>> before = analysisState();
        inputs.clear();
        // when / then
        save(fullRequest()).andExpect(status().isOk());
        JsonNode recommendationInput = inputs.stream().filter(value -> value.has("allowedReferences")).findFirst().orElseThrow();
        assertThat(recommendationInput.path("currentStoredAnalysis").path("summary").asString()).isEqualTo("시뮬레이션 요약");
        assertThat(recommendationInput.path("userTarget").path("preparationPeriod").asString()).isEqualTo("약 6개월");
        assertThat(analysisState()).isEqualTo(before);
    }

    @Test
    @DisplayName("불완전한 기존 CURRENT 진단은 제외하고 저장하지 않은 현재 분석으로 비교한다")
    void 기존결과불완전_조회롤백전파없이_성공() throws Exception {
        // given
        runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(profile).job(job).createdAt(LocalDateTime.now())
                .status(SpecProfileAnalyzationStatus.COMPLETED).specProfileSnapshotJson(jsonMapper.writeValueAsString(
                        snapshotMapper.toSnapshot(queryService.getCurrentProfile(member.getId())))).build());
        // when / then
        save(fullRequest()).andExpect(status().isOk());
        assertThat(runs.count()).isEqualTo(1);
        assertThat(results.count()).isZero();
    }

    @Test
    @DisplayName("AI 통신 실패는 기존 TARGET과 하위 스펙을 보존한다")
    void AI실패_기존목표보존_성공() throws Exception {
        // given
        long id = data(save(fullRequest()).andExpect(status().isOk())).path("selectedTargetSpecProfileId").asLong();
        List<String> previousTitles = experiences.findAllBySpecProfileId(id).stream().map(Experience::getTitle).toList();
        failRecommendation = true;
        // when / then
        save(fullRequest().replace("사용자 인턴", "저장하면 안 되는 목표")).andExpect(status().isServiceUnavailable());
        assertThat(experiences.findAllBySpecProfileId(id).stream().map(Experience::getTitle).toList()).isEqualTo(previousTitles);
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 목표와 같은 AI 대안은 한 번 재생성 후 502로 처리하고 저장하지 않는다")
    void 중복대안_제한재생성후_실패() throws Exception {
        // given
        SpecProfileTargetChanges userChanges = jsonMapper.readValue(fullRequest(), SpecProfileTargetSaveReqDto.class).changes();
        recommendation = () -> SpecProfileTargetRecommendationAiResDto.builder().options(List.of(
                new SpecProfileTargetRecommendationAiResDto.Option("이름만 다른 사용자 목표", userChanges, "중복"),
                validRecommendations().options().get(1))).build();
        // when / then
        save(fullRequest()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("TARGET_502_1"));
        assertThat(recommendationCalls.get()).isEqualTo(2);
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isZero();
    }

    @Test
    @DisplayName("잘못된 첫 생성안과 오류를 다시 전달하여 유효한 대안을 한 번 재생성한다")
    void 실패피드백전달_재생성_성공() throws Exception {
        // given
        SpecProfileTargetChanges userChanges = jsonMapper.readValue(fullRequest(), SpecProfileTargetSaveReqDto.class).changes();
        recommendation = () -> recommendationCalls.get() == 1 ? new SpecProfileTargetRecommendationAiResDto(List.of(
                new SpecProfileTargetRecommendationAiResDto.Option("중복 사용자 목표", userChanges, "중복"),
                validRecommendations().options().get(1))) : validRecommendations();
        // when / then
        save(fullRequest()).andExpect(status().isOk());
        assertThat(recommendationCalls.get()).isEqualTo(2);
        List<JsonNode> attempts = inputs.stream().filter(value -> value.has("allowedReferences")).toList();
        assertThat(attempts.get(1).path("validationFeedback").asString()).contains("previousOptions", "중복 사용자 목표");
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isEqualTo(1);
    }

    @Test
    @DisplayName("최종 분석을 종합 평가해 더 나은 대안이 아니면 사용자 TARGET도 저장하지 않는다")
    void 종합평가탈락_미저장_실패() throws Exception {
        // given
        rejectEvaluation = true;
        // when / then
        save(fullRequest()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("TARGET_502_2"));
        assertThat(evaluationCalls.get()).isEqualTo(2);
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isZero();
    }

    @Test
    @DisplayName("분석 도중 CURRENT 변경을 감지하면 목표 저장 전에 409를 반환한다")
    void 분석중CURRENT변경_낡은목표저장_실패() throws Exception {
        // given
        changeCurrentDuringEvaluation = true;
        // when / then
        save(fullRequest()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TARGET_409_1"));
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isZero();
        assertThat(analysisState().values()).allSatisfy(value -> assertThat(value).isEmpty());
    }

    @Test
    @DisplayName("하위 스펙 저장 실패 시 TARGET 프로필과 먼저 저장한 하위 스펙도 롤백한다")
    void 목표저장실패_전체롤백_성공() throws Exception {
        // given
        jdbc.execute("ALTER TABLE experience ADD CONSTRAINT target_test_reject CHECK (title <> '사용자 인턴')");
        try {
            // when / then
            save(fullRequest()).andExpect(status().isInternalServerError());
            assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isZero();
            assertThat(academics.count()).isEqualTo(1);
            assertThat(certificates.count()).isEqualTo(1);
            assertThat(scores.count()).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE experience DROP CONSTRAINT target_test_reject");
        }
    }

    @Test
    @DisplayName("동시 PUT 두 건은 CURRENT 잠금으로 같은 사용자 TARGET 슬롯을 갱신한다")
    void 동시목표저장_슬롯하나유지_성공() throws Exception {
        // given
        evaluationBarrier = new CyclicBarrier(2);
        String request = fullRequest();
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // when
            java.util.concurrent.Future<Long> first = executor.submit(() -> data(save(request).andExpect(status().isOk()))
                    .path("selectedTargetSpecProfileId").asLong());
            java.util.concurrent.Future<Long> second = executor.submit(() -> data(save(request).andExpect(status().isOk()))
                    .path("selectedTargetSpecProfileId").asLong());
            // then
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(second.get(30, TimeUnit.SECONDS));
            assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.TARGET)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("인증·소유권·날짜·참조 오류는 AI 호출 전에 거절한다")
    void 요청검증_미인증과타인스펙및잘못된날짜_실패() throws Exception {
        // given / when / then
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(fullRequest())).andExpect(status().isUnauthorized());
        Member other = members.saveAndFlush(new Member(UUID.randomUUID() + "@other.test"));
        SpecProfile otherProfile = profiles.saveAndFlush(SpecProfile.builder().member(other).build());
        ObjectNode request = (ObjectNode) jsonMapper.readTree(fullRequest());
        request.put("baseSpecProfileId", otherProfile.getId());
        save(request.toString()).andExpect(status().isNotFound());
        save(fullRequest().replace("2027-06", "2026-01")).andExpect(status().isBadRequest());
        save(fullRequest().replace("\"majorId\":" + major.getId(), "\"majorId\":9223372036854775807"))
                .andExpect(status().isNotFound());
        assertThat(simulationCalls.get()).isZero();
        assertThat(recommendationCalls.get()).isZero();
    }

    private ResultActions save(String request) throws Exception {
        return mvc.perform(put(URL).header("Authorization", "Bearer " + jwt.createAccessToken(member.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private ResultActions simulate(String request) throws Exception {
        return mvc.perform(post(URL).header("Authorization", "Bearer " + jwt.createAccessToken(member.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private JsonNode data(ResultActions response) throws Exception {
        return jsonMapper.readTree(response.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private String fullRequest() {
        return """
                {"baseSpecProfileId":%d,"companyGroupScale":"LARGE","changes":{
                  "academicAbilities":[{"type":"UNIVERSITY","status":"GRADUATED","location":"SEOUL",
                    "universityGrade":4.0,"universityStandardGrade":4.5,"graduatedYearMonth":"2027-02",
                    "majorAffiliation":"ENGINEERING","majorId":%d}],
                  "certificates":[{"certificateId":%d,"acquiredYearMonth":null}],
                  "languageExamScores":[{"languageExamId":7,"scoreGrade":"IH","acquiredYearMonth":null,"expiresYearMonth":null}],
                  "experiences":[{"type":"INTERNSHIP","title":"사용자 인턴","startedYearMonth":"2027-01",
                    "endedYearMonth":"2027-06","description":"개발 경험","awarded":false}]
                }}
                """.formatted(profile.getId(), major.getId(), targetCertificate.getId());
    }

    private SpecProfileTargetRecommendationAiResDto validRecommendations() {
        return SpecProfileTargetRecommendationAiResDto.builder().options(List.of(
                new SpecProfileTargetRecommendationAiResDto.Option("자격증 집중 준비",
                        new SpecProfileTargetChanges(null, List.of(new SpecProfileTargetChanges.CertificateChange(targetCertificate.getId(), null)), null, null),
                        "사용자 목표보다 짧은 준비 기간과 낮은 비용"),
                new SpecProfileTargetRecommendationAiResDto.Option("실무 포트폴리오",
                        new SpecProfileTargetChanges(null, null, null, List.of(new SpecProfileTargetChanges.ExperienceChange(
                                ExperienceType.PROJECT, "직무 포트폴리오", java.time.YearMonth.of(2027, 1), null,
                                "직무 프로젝트", false, null, null))), "직무 활용도와 준비 부담을 고려"))).build();
    }

    private SpecProfileSimulationAiResDto simulationResponse(long salary, String period) {
        return new SpecProfileSimulationAiResDto("시뮬레이션 요약", List.of(
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(5, salary + 18000000, salary + 20000000, salary + 22000000),
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(0, salary - 2000000, salary, salary + 2000000),
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(3, salary + 8000000, salary + 10000000, salary + 12000000)),
                "KEEP 비교 표본", "준비해 보세요.", period, "약 35만 원", "실무 경험을 중시하는 채용에 적합해요.");
    }

    private Map<String, List<Map<String, Object>>> analysisState() {
        Map<String, List<Map<String, Object>>> state = new LinkedHashMap<>();
        for (String table : ANALYSIS_TABLES) state.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        return state;
    }
}
