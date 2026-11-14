package com.lifelog.domain.specprofile;

import com.lifelog.domain.job.entity.DesiredJob;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.*;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResDto;
import com.lifelog.domain.specprofile.dto.AcademicAbilitySaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.event.SpecProfileAnuualSalaryAnalyzationRequestEvent;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.domain.specprofile.service.SpecProfileAnalyzationService;
import com.lifelog.domain.specprofile.service.SpecProfileAnnualSalaryAnalyzationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.lifelog.domain.specprofile.service.SpecProfileSaveService;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:spec-analysis;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@RecordApplicationEvents
class SpecProfileAnalyzationIntegrationTest {
    @MockitoBean SpecProfileAnnualSalaryAnalyzationService annualSalaryAnalyzationService;
    @Autowired ApplicationEvents applicationEvents;
    @Autowired SpecProfileAnalyzationService service;
    @Autowired SpecProfileSaveService saveService;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @Autowired SpecProfileAnalyzationEventLogRepository events;
    @Autowired SpecProfileRepository profiles;
    @Autowired MemberRepository members;
    @Autowired JobRepository jobs;
    @Autowired DesiredJobRepository desiredJobs;
    @Autowired MajorRepository majors;
    @Autowired AcademicAbilityRepository academics;
    @Autowired AcademicAbilityMajorRepository academicMajors;
    @Autowired LanguageExamRepository exams;
    @Autowired JsonMapper jsonMapper;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    Member member;
    SpecProfile profile;
    Job job;
    Long majorId;

    @BeforeEach
    void prepare() {
        clean();
        member = members.saveAndFlush(new Member("analysis@example.com"));
        profile = profiles.saveAndFlush(SpecProfile.builder().member(member)
                .birthYear((short) 1999).residenceRegion(ResidentialArea.SEOUL).build());
        job = jobs.saveAndFlush(Job.builder().code("0001").name("개발").mainCategoryName("개발").build());
        desiredJobs.saveAndFlush(DesiredJob.builder().member(member).job(job).build());
        Major major = majors.saveAndFlush(Major.builder().name("전공").code("TEST")
                .mainField(MajorMainField.ENGINEERING).middleField(MajorMiddleField.OTHER)
                .subField(MajorSubField.NURSING).degreeProcess(DegreeProcess.UNIVERSITY).build());
        majorId = major.getId();
        AcademicAbility academic = academics.saveAndFlush(AcademicAbility.builder().specProfile(profile)
                .type(AcademicAbilityType.UNIVERSITY).status(AcademicAbilityStatus.GRADUATED)
                .location(ResidentialArea.SEOUL).graduatedAt(LocalDate.of(2024, 2, 1)).build());
        academicMajors.saveAndFlush(AcademicAbilityMajor.builder().academicAbility(academic).major(major).build());
        exams.saveAndFlush(LanguageExam.builder().id(1L).name("시험").languageType(LanguageType.ENGLISH)
                .scoreType(ScoreType.NUMERIC).build());
    }

    @AfterEach
    void clean() {
        for (String table : List.of("spec_profile_analyzation_event_log", "spec_profile_analyzation_run",
                "academic_ability_major", "academic_ability", "spec_profile_certificate", "language_exam_score",
                "experience", "desired_job", "spec_profile", "member_identity", "member", "major", "job", "language_exam")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("회원의 CURRENT 스펙과 접수 이벤트를 저장하고 명세 응답을 반환한다")
    void API_접수및스냅샷저장_성공(CapturedOutput output) throws Exception {
        // given / when
        String body = mvc.perform(post("/api/spec-profile/analyzation/run")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("연봉 분석 요청이 접수되었습니다."))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.totalSteps").value(9))
                .andExpect(jsonPath("$.data.completedStepCount").value(0))
                .andExpect(jsonPath("$.data.steps[0].name").value("분석 실행 시작 및 상태 변경"))
                .andExpect(jsonPath("$.data.steps[8].name").value("분석 결과 저장 및 완료 처리"))
                .andReturn().getResponse().getContentAsString();
        // then
        SpecProfileAnalyzationRun run = runs.findAll().getFirst();
        JsonNode response = jsonMapper.readTree(body).get("data");
        List<String> expectedStepCodes = List.of("RUN_START", "SNAPSHOT_LOAD", "CLASSIFICATION_DATA_LOAD",
                "SCORE_BAND_CLASSIFICATION", "SAMPLE_SELECTION", "STATISTICS_AND_PROMPT_BUILD",
                "SALARY_PREDICTION", "RESULT_COMPOSITION", "RESULT_SAVE");
        JsonNode steps = response.get("steps");
        assertThat(steps.size()).isEqualTo(expectedStepCodes.size());
        for (int index = 0; index < expectedStepCodes.size(); index++) {
            assertThat(steps.get(index).get("code").asString()).isEqualTo(expectedStepCodes.get(index));
            assertThat(steps.get(index).get("order").asInt()).isEqualTo(index + 1);
        }
        assertThat(response.get("runId").asLong()).isEqualTo(run.getId());
        assertThat(response.get("specProfileId").asLong()).isEqualTo(profile.getId());
        assertThat(response.get("eventsUrl").asString()).isEqualTo("/api/spec-profile/analyzation/runs/" + run.getId() + "/events");
        assertThat(response.get("resultUrl").asString()).isEqualTo("/api/spec-profile/analyzation/runs/" + run.getId());
        assertThat(response.get("requestedAt").asString()).endsWith("+09:00");
        JsonNode snapshot = jsonMapper.readTree(run.getSpecProfileSnapshotJson());
        assertThat(snapshot.has("schemaVersion")).isFalse();
        assertThat(snapshot.get("birthYear").asInt()).isEqualTo(1999);
        assertThat(snapshot.get("jobId").asLong()).isEqualTo(job.getId());
        assertThat(snapshot.get("academicAbilities").size()).isEqualTo(1);
        assertThat(run.getDeleteAt()).isNull();
        assertThat(run.getCanceledAt()).isNull();
        assertThat(events.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getSortOrder()).isZero();
            assertThat(event.getStatus()).isEqualTo(SpecProfileAnalyzationStatus.QUEUED);
            assertThat(event.getCreatedAt()).isEqualTo(run.getCreatedAt());
        });
        assertThat(output).contains("afterCommit() - START", "afterCommit() - END");
        assertThat(applicationEvents.stream(SpecProfileAnuualSalaryAnalyzationRequestEvent.class)
                .map(SpecProfileAnuualSalaryAnalyzationRequestEvent::runId)).containsExactly(run.getId());
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"QUEUED", "IN_PROGRESS"})
    @DisplayName("최신 실행이 대기 또는 진행 중이면 접수와 임시 저장을 모두 거절한다")
    void 실행중_접수및수정_실패(SpecProfileAnalyzationStatus status) {
        // given
        saveRun(profile, status, LocalDateTime.now());
        // when / then
        assertThatThrownBy(() -> service.requestRun(member.getId())).isInstanceOfSatisfying(CustomException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.ANALYSIS_ALREADY_RUNNING));
        assertThatThrownBy(() -> saveService.saveTemporary(member.getId(), saveRequest()))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.ANALYSIS_ALREADY_RUNNING));
        assertThat(runs.count()).isEqualTo(1);
        assertThat(events.count()).isZero();
        assertThat(profiles.findById(profile.getId()).orElseThrow().getBirthYear()).isEqualTo((short) 1999);
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"COMPLETED", "FAILED", "CANCELED"})
    @DisplayName("동일 시각의 최신 PK가 종료 상태이면 과거 실행과 관계없이 수정과 재접수를 허용한다")
    void 최신종료실행_수정및재접수_성공(SpecProfileAnalyzationStatus status) {
        // given
        LocalDateTime time = LocalDateTime.now().minusDays(1);
        saveRun(profile, SpecProfileAnalyzationStatus.QUEUED, time);
        saveRun(profile, status, time);
        // when
        saveService.saveTemporary(member.getId(), saveRequest());
        SpecProfileAnalyzationRunResDto response = service.requestRun(member.getId());
        // then
        assertThat(response.status()).isEqualTo(SpecProfileAnalyzationStatus.QUEUED);
        assertThat(runs.count()).isEqualTo(3);
        assertThat(profiles.findById(profile.getId()).orElseThrow().getBirthYear()).isEqualTo((short) 2000);
    }

    @Test
    @DisplayName("다른 프로필의 실행은 CURRENT 접수에 영향을 주지 않는다")
    void 다른프로필실행_접수_성공() {
        // given
        SpecProfile target = profiles.saveAndFlush(SpecProfile.builder().member(member)
                .type(SpecProfileType.TARGET).sortOrder((short) 1).build());
        saveRun(target, SpecProfileAnalyzationStatus.IN_PROGRESS, LocalDateTime.now());
        // when / then
        assertThat(service.requestRun(member.getId()).specProfileId()).isEqualTo(profile.getId());
    }

    @Test
    @DisplayName("미입력 기본정보와 학력이 없는 스펙은 실행을 저장하지 않는다")
    void 필수값및학력누락_접수_실패() {
        // given
        academicMajors.deleteAllInBatch();
        academics.deleteAllInBatch();
        // when / then
        assertInvalidInput();
        jdbc.update("UPDATE spec_profile SET birth_year = NULL WHERE spec_profile_id = ?", profile.getId());
        assertInvalidInput();
        assertThat(runs.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE spec_profile SET birth_year = NULL",
            "UPDATE spec_profile SET birth_year = 3000",
            "UPDATE spec_profile SET residence_region = NULL",
            "DELETE FROM desired_job",
            "DELETE FROM academic_ability_major",
            "UPDATE academic_ability SET graduated_at = NULL",
            "UPDATE academic_ability SET university_grade = 4, university_standard_grade = 3"
    })
    @DisplayName("저장된 필수값과 학력 조건이 잘못되면 400으로 거절한다")
    void 저장값오류_API_실패(String invalidData) throws Exception {
        // given
        jdbc.update(invalidData);
        // when / then
        mvc.perform(post("/api/spec-profile/analyzation/run")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(runs.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test
    @DisplayName("실행 저장 후 외부 트랜잭션이 롤백되면 이력과 이벤트 및 커밋 콜백을 남기지 않는다")
    void 트랜잭션롤백_이력및콜백없음_성공(CapturedOutput output) {
        // given / when
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            service.requestRun(member.getId());
            assertThat(output).doesNotContain("afterCommit() - START");
            assertThat(applicationEvents.stream(SpecProfileAnuualSalaryAnalyzationRequestEvent.class)).isEmpty();
            transaction.setRollbackOnly();
        });
        // then
        assertThat(runs.count()).isZero();
        assertThat(events.count()).isZero();
        assertThat(output).doesNotContain("afterCommit() - START");
        assertThat(applicationEvents.stream(SpecProfileAnuualSalaryAnalyzationRequestEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("이벤트 INSERT가 실패하면 먼저 저장한 실행도 롤백한다")
    void 이벤트저장실패_실행롤백_성공(CapturedOutput output) {
        // given
        jdbc.execute("ALTER TABLE spec_profile_analyzation_event_log ADD CONSTRAINT reject_analysis_event CHECK (sort_order < 0)");
        try {
            // when / then
            assertThatThrownBy(() -> service.requestRun(member.getId())).isInstanceOf(RuntimeException.class);
            assertThat(runs.count()).isZero();
            assertThat(events.count()).isZero();
            assertThat(output).doesNotContain("afterCommit() - START");
        } finally {
            jdbc.execute("ALTER TABLE spec_profile_analyzation_event_log DROP CONSTRAINT reject_analysis_event");
        }
    }

    @Test
    @DisplayName("분석 종료 후 현재 스펙을 수정해도 접수 시점 스냅샷은 변하지 않는다")
    void 스펙수정_과거스냅샷유지_성공() {
        // given
        Long runId = service.requestRun(member.getId()).runId();
        String snapshot = runs.findById(runId).orElseThrow().getSpecProfileSnapshotJson();
        jdbc.update("UPDATE spec_profile_analyzation_run SET status = 'COMPLETED' WHERE run_id = ?", runId);
        // when
        saveService.saveTemporary(member.getId(), saveRequest());
        // then
        assertThat(profiles.findById(profile.getId()).orElseThrow().getBirthYear()).isEqualTo((short) 2000);
        assertThat(runs.findById(runId).orElseThrow().getSpecProfileSnapshotJson()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("동시 접수 두 건 중 하나만 성공하고 실행과 이벤트를 한 건씩 저장한다")
    void 동시접수_하나만성공() throws Exception {
        // given
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> request = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 시간 초과");
                try {
                    service.requestRun(member.getId());
                    return true;
                } catch (CustomException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.ANALYSIS_ALREADY_RUNNING);
                    return false;
                }
            };
            // when
            Future<Boolean> first = executor.submit(request);
            Future<Boolean> second = executor.submit(request);
            start.countDown();
            // then
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(runs.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("임시 저장과 분석 접수가 경합해도 현재 값과 스냅샷이 일치한다")
    void 저장및접수경합_일관된스냅샷_성공() throws Exception {
        // given
        CountDownLatch start = new CountDownLatch(1);
        SpecProfileTemporarySaveReqDto request = saveRequest();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SpecProfileAnalyzationRunResDto> analysis = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 시간 초과");
                return service.requestRun(member.getId());
            });
            Future<Boolean> save = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 시간 초과");
                try {
                    saveService.saveTemporary(member.getId(), request);
                    return true;
                } catch (CustomException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.ANALYSIS_ALREADY_RUNNING);
                    return false;
                }
            });
            // when
            start.countDown();
            Long runId = analysis.get(30, TimeUnit.SECONDS).runId();
            boolean saved = save.get(30, TimeUnit.SECONDS);
            // then
            int birthYear = profiles.findById(profile.getId()).orElseThrow().getBirthYear();
            assertThat(birthYear).isEqualTo(saved ? 2000 : 1999);
            assertThat(jsonMapper.readTree(runs.findById(runId).orElseThrow().getSpecProfileSnapshotJson())
                    .get("birthYear").asInt()).isEqualTo(birthYear);
        }
        assertThat(runs.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("인증 없는 접수는 거절한다")
    void API_미인증_실패() throws Exception {
        // given / when / then
        mvc.perform(post("/api/spec-profile/analyzation/run")).andExpect(status().isUnauthorized());
        assertThat(runs.count()).isZero();
    }

    @Test
    @DisplayName("CURRENT가 없으면 404로 거절한다")
    void CURRENT없음_실패() {
        // given
        academicMajors.deleteAllInBatch();
        academics.deleteAllInBatch();
        profiles.deleteAllInBatch();
        // when / then
        assertThatThrownBy(() -> service.requestRun(member.getId())).isInstanceOfSatisfying(CustomException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.NOT_FOUND));
    }

    private void saveRun(SpecProfile owner, SpecProfileAnalyzationStatus status, LocalDateTime time) {
        runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(owner).job(job).createdAt(time)
                .status(status).canceledAt(status == SpecProfileAnalyzationStatus.CANCELED ? time : null)
                .specProfileSnapshotJson("{}").build());
    }

    private SpecProfileTemporarySaveReqDto saveRequest() {
        return new SpecProfileTemporarySaveReqDto(2000, ResidentialArea.DAEGU, job.getId(),
                List.of(new AcademicAbilitySaveReqDto(
                        academics.findAll().getFirst().getId(), AcademicAbilityType.UNIVERSITY,
                        AcademicAbilityStatus.GRADUATED, ResidentialArea.SEOUL, null, null,
                        YearMonth.of(2024, 2), MajorMainField.ENGINEERING, majorId)),
                List.of(), List.of(), List.of());
    }

    private void assertInvalidInput() {
        assertThatThrownBy(() -> service.requestRun(member.getId())).isInstanceOfSatisfying(CustomException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.INVALID_REQUEST));
    }
}
