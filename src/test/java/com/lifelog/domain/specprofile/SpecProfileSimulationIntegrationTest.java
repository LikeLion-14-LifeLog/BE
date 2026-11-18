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
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAiResDto;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.AcademicAbilityMajor;
import com.lifelog.domain.specprofile.entity.Experience;
import com.lifelog.domain.specprofile.entity.LanguageExamScore;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileCertificate;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.ExperienceType;
import com.lifelog.domain.specprofile.repository.AcademicAbilityMajorRepository;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.ExperienceRepository;
import com.lifelog.domain.specprofile.repository.LanguageExamScoreRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileCertificateRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenApiErrorCode;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import com.lifelog.global.infra.openai.OpenApiUtil;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:salary-simulation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false",
        "keep.salary.csv-path=classpath:data/keep_salary_preparation.csv"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileSimulationIntegrationTest {
    private static final String URL = "/api/spec-profile/target";
    private static final List<String> SPEC_TABLES = List.of("spec_profile", "academic_ability", "academic_ability_major",
            "spec_profile_certificate", "language_exam_score", "experience", "desired_job",
            "spec_profile_analyzation_run", "spec_profile_analyzation_event_log", "spec_profile_analyzation_result");

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
    @MockitoBean OpenApiUtil openApiUtil;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationCacheRegistry cacheRegistry;

    final List<JsonNode> inputs = new ArrayList<>();
    Member member;
    SpecProfile profile;
    Major major;
    Certification ownedCertificate;
    Certification targetCertificate;

    @BeforeEach
    void 준비() {
        member = members.saveAndFlush(new Member(UUID.randomUUID() + "@simulation.test"));
        profile = profiles.saveAndFlush(SpecProfile.builder().member(member).birthYear((short) 1999)
                .residenceRegion(ResidentialArea.SEOUL).build());
        Job job = jobs.saveAndFlush(Job.builder().code("1332").name("개발자").mainCategoryName("연구직")
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
        ownedCertificate = saveCertificate("기존 자격증", "SIM1");
        targetCertificate = saveCertificate("목표 자격증", "SIM2");
        certificates.saveAndFlush(SpecProfileCertificate.builder().specProfile(profile).certificate(ownedCertificate)
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
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            OpenApiPrompt prompt = invocation.getArgument(0);
            assertThat(prompt.schemaName()).isEqualTo("annual_salary_simulation_v1");
            JsonNode input = jsonMapper.readTree(prompt.inputJson());
            inputs.add(input);
            return aiResponse(input.path("snapshot").path("certificates").size() > 1 ? 41000000L : 36000000L);
        }).when(openApiUtil).request(any(OpenApiPrompt.class), eq(SpecProfileSimulationAiResDto.class));
    }

    @AfterEach
    void 정리() {
        cacheRegistry.evict(member.getId());
        for (String table : List.of("academic_ability_major", "academic_ability", "spec_profile_certificate",
                "language_exam_score", "experience", "desired_job", "spec_profile", "major", "certificate",
                "language_exam", "job", "member")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("현재 스펙을 모두 유지하고 목표 항목을 추가하며 종료·취득 연월은 무시하고 DB를 보존한다")
    void 목표추가_날짜무시_미저장_성공() throws Exception {
        // given
        Map<String, List<Map<String, Object>>> before = databaseState();
        // when / then
        simulate(fullRequest()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("연봉 시뮬레이션 성공"))
                .andExpect(jsonPath("$.data.baseSpecProfileId").value(profile.getId()))
                .andExpect(jsonPath("$.data.companyGroupScale").value("LARGE"))
                .andExpect(jsonPath("$.data.base.salaryResults[0].annual").value(0))
                .andExpect(jsonPath("$.data.base.salaryResults[1].annual").value(3))
                .andExpect(jsonPath("$.data.base.salaryResults[2].annual").value(5))
                .andExpect(jsonPath("$.data.simulated.salaryResults[0].annual").value(0))
                .andExpect(jsonPath("$.data.simulated.salaryResults[1].annual").value(3))
                .andExpect(jsonPath("$.data.simulated.salaryResults[2].annual").value(5))
                .andExpect(jsonPath("$.data.base.salaryResults[0].annualIncomeExpected").value(36000000))
                .andExpect(jsonPath("$.data.simulated.salaryResults[0].annualIncomeExpected").value(41000000))
                .andExpect(jsonPath("$.data.base.salaryResults[0].topPercent").value(100.0))
                .andExpect(jsonPath("$.data.simulated.salaryResults[0].topPercent").value(0.0))
                .andExpect(jsonPath("$.data.preparationPeriod").value("약 6개월"))
                .andExpect(jsonPath("$.data.estimatedCost").value("약 35만 원"))
                .andExpect(jsonPath("$.data.decisionCriteria").value("실무 경험을 중시하는 채용에 적합해요."))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"topPercent\":null")))
                .andExpect(jsonPath("$.data.base.summary").doesNotExist());
        assertThat(inputs).hasSize(2).allSatisfy(input -> assertThat(input.path("companyGroupScale").asString()).isEqualTo("LARGE"));
        JsonNode base = inputs.get(0).path("snapshot");
        JsonNode target = inputs.get(1).path("snapshot");
        assertThat(inputs.get(1).path("baseSnapshot")).isEqualTo(base);
        for (String field : List.of("academicAbilities", "certificates", "languageExamScores", "experiences")) {
            assertThat(base.path(field).size()).isEqualTo(1);
            assertThat(target.path(field).size()).isEqualTo(2);
            assertThat(target.path(field).get(0)).isEqualTo(base.path(field).get(0));
        }
        assertThat(target.path("academicAbilities").get(1).path("majorName").asString()).isEqualTo(major.getName());
        assertThat(target.path("academicAbilities").get(1).path("graduatedYearMonth").isNull()).isTrue();
        assertThat(target.path("certificates").get(1).path("acquiredYearMonth").isNull()).isTrue();
        assertThat(target.path("languageExamScores").get(1).path("acquiredYearMonth").isNull()).isTrue();
        assertThat(target.path("languageExamScores").get(1).path("expiresYearMonth").isNull()).isTrue();
        assertThat(target.path("experiences").get(1).path("endedYearMonth").isNull()).isTrue();
        assertThat(target.path("experiences").get(1).path("startedYearMonth").asString()).isEqualTo("2099-01");
        assertThat(databaseState()).isEqualTo(before);
        assertThat(cacheRegistry.find(member.getId()).orElseThrow().simulatedAnalysis().salaryResults().get(0).annualIncomeExpected()).isEqualTo(41000000L);
        assertThat(cacheRegistry.find(member.getId()).orElseThrow().response()).satisfies(response -> {
            assertThat(response.preparationPeriod()).isEqualTo("약 6개월");
            assertThat(response.estimatedCost()).isEqualTo("약 35만 원");
            assertThat(response.decisionCriteria()).isEqualTo("실무 경험을 중시하는 채용에 적합해요.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"changes\":null", ",\"changes\":{}",
            ",\"changes\":{\"academicAbilities\":[],\"certificates\":[],\"languageExamScores\":[],\"experiences\":[]}"})
    @DisplayName("변경 조건이 없으면 기업 규모 null과 simulated null을 반환하고 현재만 예측한다")
    void 변경없음_현재만분석_성공(String changes) throws Exception {
        // given
        String request = "{\"baseSpecProfileId\":" + profile.getId() + changes + "}";
        Map<String, List<Map<String, Object>>> before = databaseState();
        // when / then
        simulate(request).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"simulated\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"companyGroupScale\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"preparationPeriod\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"estimatedCost\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"decisionCriteria\":null")));
        assertThat(inputs).singleElement().satisfies(input -> assertThat(input.path("companyGroupScale").isNull()).isTrue());
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    @DisplayName("이전 목표를 저장하거나 다음 호출에 누적하지 않는다")
    void 연속요청_목표격리_성공() throws Exception {
        // given / when
        simulate(fullRequest()).andExpect(status().isOk());
        Object first = cacheRegistry.find(member.getId()).orElseThrow();
        simulate("{\"baseSpecProfileId\":" + profile.getId() + "}").andExpect(status().isOk());
        // then
        assertThat(inputs).hasSize(3);
        assertThat(inputs.get(2).path("snapshot").path("certificates").size()).isEqualTo(1);
        assertThat(cacheRegistry.find(member.getId()).orElseThrow()).isNotSameAs(first);
        assertThat(cacheRegistry.find(member.getId()).orElseThrow().simulated()).isNull();
    }

    @Test
    @DisplayName("목표 예측 타임아웃은 전체 요청을 실패시키며 분석 실패 이력도 생성하지 않는다")
    void 목표분석실패_미저장_성공() throws Exception {
        // given
        Map<String, List<Map<String, Object>>> before = databaseState();
        doAnswer(invocation -> {
            OpenApiPrompt prompt = invocation.getArgument(0);
            if (jsonMapper.readTree(prompt.inputJson()).path("snapshot").path("certificates").size() > 1) {
                throw new CustomException(OpenApiErrorCode.OPEN_API_TIMEOUT);
            }
            return aiResponse(36000000L);
        }).when(openApiUtil).request(any(OpenApiPrompt.class), eq(SpecProfileSimulationAiResDto.class));
        // when / then
        simulate(fullRequest()).andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("OPEN_API_TIMEOUT"));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    @DisplayName("다른 회원·미존재 기준 프로필과 미인증 요청을 거절한다")
    void 소유권과인증_실패() throws Exception {
        // given
        Member other = members.saveAndFlush(new Member(UUID.randomUUID() + "@other.test"));
        SpecProfile otherProfile = profiles.saveAndFlush(SpecProfile.builder().member(other).build());
        // when / then
        for (Long profileId : List.of(otherProfile.getId(), Long.MAX_VALUE)) {
            simulate("{\"baseSpecProfileId\":" + profileId + "}")
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_404_1"));
        }
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(fullRequest()))
                .andExpect(status().isUnauthorized());
        assertThat(inputs).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"preparationPeriod", "estimatedCost", "decisionCriteria"})
    @DisplayName("AI가 준비 기간·예상 비용·판단 기준을 누락하면 불완전한 결과를 반환하거나 캐싱하지 않는다")
    void 준비정보누락_실패(String field) throws Exception {
        // given
        Map<String, List<Map<String, Object>>> before = databaseState();
        ObjectNode response = jsonMapper.valueToTree(aiResponse(36000000L));
        response.remove(field);
        SpecProfileSimulationAiResDto invalid = jsonMapper.treeToValue(response, SpecProfileSimulationAiResDto.class);
        doAnswer(invocation -> invalid).when(openApiUtil)
                .request(any(OpenApiPrompt.class), eq(SpecProfileSimulationAiResDto.class));
        // when / then
        simulate(fullRequest()).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVALID_AI_RESPONSE"));
        assertThat(cacheRegistry.find(member.getId())).isEmpty();
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    @DisplayName("잘못된 기업 규모와 누락·음수 프로필 ID를 400으로 거절한다")
    void 기본입력검증_실패() throws Exception {
        // given / when / then
        for (String request : List.of("{}", "{\"baseSpecProfileId\":-1}",
                "{\"baseSpecProfileId\":" + profile.getId() + ",\"companyGroupScale\":\"INVALID\"}")) {
            simulate(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        }
        assertThat(inputs).isEmpty();
    }

    @Test
    @DisplayName("미등록 참조와 기존·요청 내 중복 자격증 및 잘못된 성적 조합을 거절한다")
    void 목표조건검증_실패() throws Exception {
        // given
        ObjectNode missing = (ObjectNode) jsonMapper.readTree(fullRequest());
        ((ObjectNode) missing.path("changes").path("certificates").get(0)).put("certificateId", Long.MAX_VALUE);
        ObjectNode duplicate = (ObjectNode) jsonMapper.readTree(fullRequest());
        ((ObjectNode) duplicate.path("changes").path("certificates").get(0)).put("certificateId", ownedCertificate.getId());
        ObjectNode duplicateRequest = (ObjectNode) jsonMapper.readTree(fullRequest());
        duplicateRequest.withObject("changes").putArray("certificates")
                .addObject().put("certificateId", targetCertificate.getId());
        ((tools.jackson.databind.node.ArrayNode) duplicateRequest.path("changes").path("certificates"))
                .addObject().put("certificateId", targetCertificate.getId());
        ObjectNode invalidScore = (ObjectNode) jsonMapper.readTree(fullRequest());
        ((ObjectNode) invalidScore.path("changes").path("languageExamScores").get(0)).put("scoreNumeric", 100);
        // when / then
        simulate(missing.toString()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_404_2"));
        for (ObjectNode request : List.of(duplicate, duplicateRequest)) {
            simulate(request.toString()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_400_2"));
        }
        simulate(invalidScore.toString()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_400_1"));
        assertThat(inputs).isEmpty();
    }

    @Test
    @DisplayName("미입력 목표 날짜와 100점제 학점을 허용하며 입력 만점을 넘는 학점은 거절한다")
    void 목표학점경계_성공과실패() throws Exception {
        // given
        ObjectNode request = (ObjectNode) jsonMapper.readTree(fullRequest());
        ObjectNode academic = (ObjectNode) request.path("changes").path("academicAbilities").get(0);
        academic.remove("graduatedYearMonth");
        academic.put("universityGrade", 95);
        academic.put("universityStandardGrade", 100);
        ((ObjectNode) request.path("changes").path("certificates").get(0)).remove("acquiredYearMonth");
        // when / then
        simulate(request.toString()).andExpect(status().isOk());
        assertThat(inputs.get(1).path("snapshot").path("academicAbilities").get(1)
                .path("universityGrade").asInt()).isEqualTo(95);
        academic.put("universityGrade", 101);
        simulate(request.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CLASSIFICATION_SCORE"));
        assertThat(inputs).hasSize(2);
    }

    @Test
    @DisplayName("수상 목표에 기관명이 없거나 미등록 전공·시험을 참조하면 AI 호출 전에 거절한다")
    void 목표참조와수상정보검증_실패() throws Exception {
        // given
        ObjectNode award = (ObjectNode) jsonMapper.readTree(fullRequest());
        ((ObjectNode) award.path("changes").path("experiences").get(0)).put("awarded", true);
        ObjectNode missingMajor = (ObjectNode) jsonMapper.readTree(fullRequest());
        ((ObjectNode) missingMajor.path("changes").path("academicAbilities").get(0)).put("majorId", Long.MAX_VALUE);
        ObjectNode missingExam = (ObjectNode) jsonMapper.readTree(fullRequest());
        ((ObjectNode) missingExam.path("changes").path("languageExamScores").get(0)).put("languageExamId", Long.MAX_VALUE);
        // when / then
        simulate(award.toString()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_400_1"));
        for (ObjectNode request : List.of(missingMajor, missingExam)) {
            simulate(request.toString()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_404_2"));
        }
        assertThat(inputs).isEmpty();
    }

    @Test
    @DisplayName("캐싱 실패 시 계산 결과를 정상 반환하고 이전 캐시를 제거한다")
    void 캐싱실패_정상응답_성공() throws Exception {
        // given
        simulate(fullRequest()).andExpect(status().isOk());
        org.mockito.Mockito.doThrow(new IllegalStateException("cache unavailable"))
                .when(cacheRegistry).store(any(Long.class), any());
        // when / then
        simulate(fullRequest()).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "응답 캐싱에 실패했습니다. 현재 스펙으로 저장 요청 시 별도의 AI 호출 결과값이 저장되게 됩니다")))
                .andExpect(jsonPath("$.data.simulated.salaryResults[0].annualIncomeExpected").value(41000000))
                .andExpect(jsonPath("$.data.preparationPeriod").value("약 6개월"))
                .andExpect(jsonPath("$.data.estimatedCost").value("약 35만 원"))
                .andExpect(jsonPath("$.data.decisionCriteria").value("실무 경험을 중시하는 채용에 적합해요."));
        assertThat(cacheRegistry.find(member.getId())).isEmpty();
    }

    private ResultActions simulate(String request) throws Exception {
        return mvc.perform(post(URL).header("Authorization", "Bearer " + jwt.createAccessToken(member.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private String fullRequest() {
        return """
                {"baseSpecProfileId":%d,"companyGroupScale":"LARGE","changes":{
                  "academicAbilities":[{"type":"UNIVERSITY","status":"GRADUATED","location":"SEOUL",
                    "universityGrade":4.0,"universityStandardGrade":4.5,"graduatedYearMonth":"invalid-ignored",
                    "majorAffiliation":"ENGINEERING","majorId":%d}],
                  "certificates":[{"certificateId":%d,"acquiredYearMonth":{"ignored":true}}],
                  "languageExamScores":[{"languageExamId":7,"scoreGrade":"IH",
                    "acquiredYearMonth":"invalid-ignored","expiresYearMonth":-1}],
                  "experiences":[{"type":"INTERNSHIP","title":"목표 인턴","startedYearMonth":"2099-01",
                    "endedYearMonth":"1900-01","description":"목표 개발 경험","awarded":false}]
                }}
                """.formatted(profile.getId(), major.getId(), targetCertificate.getId());
    }

    private Certification saveCertificate(String name, String code) {
        return certifications.saveAndFlush(Certification.builder().type(CertificationType.PRIVATE)
                .mainCategory(CertificationMainCategory.COMMON).name(name).code(code).build());
    }

    private Map<String, List<Map<String, Object>>> databaseState() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : SPEC_TABLES) result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        return result;
    }

    private SpecProfileSimulationAiResDto aiResponse(long firstSalary) {
        return new SpecProfileSimulationAiResDto("시뮬레이션 요약", List.of(
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(5, firstSalary + 18000000, firstSalary + 20000000, firstSalary + 22000000),
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(0, firstSalary - 2000000, firstSalary, firstSalary + 2000000),
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(3, firstSalary + 8000000, firstSalary + 10000000, firstSalary + 12000000)),
                "기존 KEEP 비교 표본", "경험을 보완해 보세요.", "약 6개월", "약 35만 원", "실무 경험을 중시하는 채용에 적합해요.");
    }
}
