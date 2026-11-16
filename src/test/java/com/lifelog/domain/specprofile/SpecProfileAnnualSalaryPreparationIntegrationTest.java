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
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationResult;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationResultRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationEventLogRepository;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationEventLog;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.lifelog.global.security.JwtProvider;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnnualSalaryAnalyzationService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationRunStateService;
import com.lifelog.domain.specprofile.service.SpecProfileSalaryScoreBandService;
import com.lifelog.domain.specprofile.service.SpecProfileAnnualSalaryResultSaveService;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenApiErrorCode;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import com.lifelog.global.infra.openai.OpenApiUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:annual-preparation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "keep.salary.csv-path=classpath:data/keep_salary_preparation.csv"
})
@ActiveProfiles("test")
class SpecProfileAnnualSalaryPreparationIntegrationTest {
    @Autowired SpecProfileAnnualSalaryAnalyzationService service;
    @MockitoSpyBean SpecProfileAnalyzationRunStateService stateService;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @Autowired SpecProfileAnalyzationResultRepository results;
    @Autowired SpecProfileAnnualSalaryResultSaveService resultSaveService;
    @Autowired JdbcTemplate jdbc;
    @Autowired SpecProfileRepository profiles;
    @Autowired MemberRepository members;
    @Autowired JobRepository jobs;
    @Autowired SpecProfileAnalyzationEventLogRepository events;
    @Autowired JwtProvider jwt;
    @LocalServerPort int port;
    @Autowired JsonMapper jsonMapper;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean SpecProfileSalaryScoreBandService scoreBandService;
    @MockitoBean OpenApiUtil openApiUtil;
    SpecProfile profile;
    Job job;
    Long memberId;

    @BeforeEach
    void 준비() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return aiResponse();
        }).when(openApiUtil).request(any(OpenApiPrompt.class), eq(AnnualSalaryAnalyzationAiResDto.class));
        Member member = members.saveAndFlush(new Member(UUID.randomUUID() + "@test.example"));
        memberId = member.getId();
        profile = profiles.saveAndFlush(SpecProfile.builder().member(member).build());
        job = jobs.findAll().stream().filter(value -> value.getCode().equals("1332")).findFirst()
                .orElseGet(() -> jobs.saveAndFlush(Job.builder().code("1332").name("개발자")
                        .mainCategoryName("연구직").subCategoryCode("133").subCategoryName("개발직").build()));
    }

    @Test
    @DisplayName("연차별 예측과 진단 문구·비교 집단·관측 통계를 저장하고 실행을 COMPLETED로 전환한다")
    void 스냅샷조회_분류조건준비_성공() {
        // given
        Long runId = saveRun(snapshotJson());
        // when
        SpecProfileAnnualSalaryAnalyzationResDto result = service.analyze(runId);
        // then
        SpecProfileAnalyzationRun completed = runs.findById(runId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
        List<SpecProfileAnalyzationResult> saved = results.findAllByRun_IdOrderByAnnualAsc(runId);
        assertThat(saved).extracting(SpecProfileAnalyzationResult::getAnnual).containsExactly(0, 3, 5);
        for (int index = 0; index < saved.size(); index++) {
            assertThat(saved.get(index).getAnnualIncomeMin()).isEqualTo(result.salaryResults().get(index).annualIncomeMin());
            assertThat(saved.get(index).getAnnualIncomeExpected()).isEqualTo(result.salaryResults().get(index).annualIncomeExpected());
            assertThat(saved.get(index).getAnnualIncomeMax()).isEqualTo(result.salaryResults().get(index).annualIncomeMax());
            assertThat(saved.get(index).getSummary()).isEqualTo(result.summary());
            assertThat(saved.get(index).getComparisonGroupDescription()).isEqualTo(result.comparisonGroup().description());
            assertThat(saved.get(index).getRecommendation()).isEqualTo(result.recommendation());
        }
        assertStoredReport(runId, result);
        assertThat(result.statistics().sampleCount()).isEqualTo(10);
        assertThat(result.statistics().annualSalaryMeanKrw()).isEqualByComparingTo("40000000");
        assertThat(result.comparisonGroup().observationYears()).containsExactly(2024);
        assertThat(result.salaryResults()).extracting(SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult::annual).containsExactly(0, 3, 5);
        assertThat(result.appliedSpecs().academicAbilities()).hasSize(2);
        assertThat(result.appliedSpecs().languageExamScores()).hasSize(2);
        assertThat(result.appliedSpecs().certificates()).hasSize(2);
        assertThat(result.salaryResults().getFirst().topPercent()).isEqualByComparingTo("100.0");
        assertThat(result.salaryResults().get(1).topPercent()).isNull();
        assertThat(jsonMapper.writeValueAsString(result)).doesNotContain("careerLabel");
        assertThat(runs.findById(runId).orElseThrow().getSpecProfileSnapshotJson()).isEqualTo(snapshotJson());
        assertThat(service.analyze(runId)).isNull();
        assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).hasSize(3);
        assertThat(stateService.failIfRunning(runId)).isFalse();
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(runId).stream()
                .filter(event -> event.getStep() != null && event.getStatus() == SpecProfileAnalyzationStatus.COMPLETED)
                .map(SpecProfileAnalyzationEventLog::getStep)).containsExactly(SpecProfileAnalyzationStep.values());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("최빈값이 여러 개이거나 없어도 통계의 소수와 관측 연도 순서를 DB에 보존한다")
    void 통계컬렉션과소수_저장후재조회_성공(boolean hasModes) {
        // given
        Long runId = saveRun(snapshotJson());
        assertThat(stateService.startIfQueued(runId)).isTrue();
        KeepSalaryMatchedSampleStatistics statistics = KeepSalaryMatchedSampleStatistics.builder()
                .sampleCount(120).annualSalaryMinKrw(new BigDecimal("20000000.25"))
                .annualSalaryMaxKrw(new BigDecimal("60000000.75"))
                .annualSalaryMeanKrw(new BigDecimal("36000000.38"))
                .annualSalaryModesKrw(hasModes ? List.of(new BigDecimal("35000000.25"), new BigDecimal("40000000.75")) : List.of())
                .modeFrequency(hasModes ? 8 : 1).annualSalaryP25Krw(new BigDecimal("30000000.13"))
                .annualSalaryMedianKrw(new BigDecimal("35000000.25"))
                .annualSalaryP75Krw(new BigDecimal("42000000.63")).build();
        SpecProfileAnnualSalaryAnalyzationResDto report = SpecProfileAnnualSalaryAnalyzationResDto.builder()
                .summary("분석 요약").salaryResults(List.of(
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(0, 34000000L, 36000000L, 38000000L, new BigDecimal("28.0")),
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(3, 41000000L, 44500000L, 48000000L, null),
                        new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(5, 50000000L, 54500000L, 60000000L, null)))
                .comparisonGroup(new SpecProfileAnnualSalaryAnalyzationResDto.ComparisonGroup("133", "개발직", 120,
                        List.of(2021, 2022, 2023, 2024), "분석 당시 비교 집단"))
                .statistics(statistics).recommendation("실무 경험 보완").salaryNotice("분석 당시 안내 문구").build();
        // when
        assertThat(resultSaveService.saveAndComplete(runId, report)).isTrue();
        // then
        assertStoredReport(runId, report);
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
        jdbc.update("DELETE FROM spec_profile_analyzation_run WHERE run_id = ?", runId);
        assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spec_profile_analyzation_result_salary_mode m LEFT JOIN spec_profile_analyzation_result r ON r.result_id = m.result_id WHERE r.result_id IS NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spec_profile_analyzation_result_observation_year y LEFT JOIN spec_profile_analyzation_result r ON r.result_id = y.result_id WHERE r.result_id IS NULL", Integer.class)).isZero();
    }

    @Test
    @DisplayName("호출자의 트랜잭션이 롤백돼도 결과와 COMPLETED는 독립 커밋으로 보존한다")
    void 단계별트랜잭션분리_성공() {
        // given
        Long runId = saveRun(snapshotJson());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(scoreBandService).classify(any(SpecProfileSalaryClassificationData.class));
        // when
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            service.analyze(runId);
            throw new IllegalStateException("호출자 롤백");
        })).isInstanceOf(IllegalStateException.class);
        // then
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
        assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).hasSize(3)
                .allSatisfy(saved -> assertThat(saved.getSummary()).isEqualTo(aiResponse().summary()));
    }

    @Test
    @DisplayName("연차별 결과를 쓴 후 완료 상태 저장이 실패하면 전체 저장을 롤백하고 FAILED로 보상한다")
    void 완료저장실패_결과롤백_FAILED저장() {
        // given
        Long runId = saveRun(snapshotJson());
        String constraint = "ck_test_completion_failure_" + runId;
        jdbc.execute("ALTER TABLE spec_profile_analyzation_run ADD CONSTRAINT " + constraint
                + " CHECK (run_id <> " + runId + " OR status <> 'COMPLETED')");
        try {
            // when / then
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> service.analyze(runId)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            SpecProfileAnalyzationRun failed = runs.findById(runId).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(SpecProfileAnalyzationStatus.FAILED);
            assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).isEmpty();
            assertThat(events.findAllByRun_IdOrderBySortOrderAsc(runId))
                    .noneMatch(event -> event.getStatus() == SpecProfileAnalyzationStatus.COMPLETED
                            && (event.getStep() == null || event.getStep() == SpecProfileAnalyzationStep.RESULT_SAVE));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spec_profile_analyzation_result_salary_mode m LEFT JOIN spec_profile_analyzation_result r ON r.result_id = m.result_id WHERE r.result_id IS NULL", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spec_profile_analyzation_result_observation_year y LEFT JOIN spec_profile_analyzation_result r ON r.result_id = y.result_id WHERE r.result_id IS NULL", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE spec_profile_analyzation_run DROP CONSTRAINT " + constraint);
        }
    }

    @Test
    @DisplayName("API 응답을 기다리는 중 취소된 실행은 결과를 저장하거나 COMPLETED로 덮어쓰지 않는다")
    void 저장전취소_완료생략_성공() throws Exception {
        // given
        Long runId = saveRun(snapshotJson());
        CountDownLatch awaitingAi = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        doAnswer(invocation -> {
            awaitingAi.countDown();
            assertThat(releaseAi.await(10, TimeUnit.SECONDS)).isTrue();
            return aiResponse();
        }).when(openApiUtil).request(any(OpenApiPrompt.class), eq(AnnualSalaryAnalyzationAiResDto.class));
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             AnalyzationSseTestClient stream = new AnalyzationSseTestClient(port, runId, jwt.createAccessToken(memberId), null)) {
            stream.nextData();
            Future<SpecProfileAnnualSalaryAnalyzationResDto> analysis = executor.submit(() -> service.analyze(runId));
            try {
                assertThat(awaitingAi.await(10, TimeUnit.SECONDS)).isTrue();
                for (int count = 1; count <= 6; count++) assertThat(stream.nextData().name()).isEqualTo("progress");
                // when: AI 응답을 반환하기 전에 실제 중단 API와 SSE 종료를 확인한다.
                stopRun(runId);
                // then
                assertThat(stream.nextData().name()).isEqualTo("canceled");
                stream.awaitClosed();
                assertThat(analysis.isDone()).isFalse();
            } finally {
                releaseAi.countDown();
            }
            assertThat(analysis.get(10, TimeUnit.SECONDS)).isNull();
        }
        SpecProfileAnalyzationRun canceled = runs.findById(runId).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(SpecProfileAnalyzationStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isNotNull();
        assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).isEmpty();
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(runId)).anyMatch(event ->
                event.getStep() == SpecProfileAnalyzationStep.SALARY_PREDICTION && event.getStatus() == SpecProfileAnalyzationStatus.CANCELED);
    }

    @Test
    @DisplayName("실제 HTTP 구독에 snapshot과 9번 progress를 전송하고 결과 커밋 후 completed로 종료한다")
    void SSE_전체분석진행_성공() throws Exception {
        // given
        Long runId = saveRun(snapshotJson());
        try (AnalyzationSseTestClient stream = new AnalyzationSseTestClient(port, runId, jwt.createAccessToken(memberId), null)) {
            assertThat(stream.nextData().name()).isEqualTo("snapshot");
            // when
            assertThat(service.analyze(runId)).isNotNull();
            // then
            for (int count = 1; count <= SpecProfileAnalyzationStep.values().length; count++) {
                AnalyzationSseTestClient.Event event = stream.nextData();
                assertThat(event.name()).isEqualTo("progress");
                assertThat(jsonMapper.readTree(event.data()).get("completedStepCount").asInt()).isEqualTo(count);
                assertThat(jsonMapper.readTree(event.data()).get("status").asString()).isEqualTo("IN_PROGRESS");
            }
            AnalyzationSseTestClient.Event terminal = stream.nextData();
            assertThat(terminal.name()).isEqualTo("completed");
            assertThat(jsonMapper.readTree(terminal.data()).get("status").asString()).isEqualTo("COMPLETED");
            assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).hasSize(3);
            stream.awaitClosed();
        }
    }

    @Test
    @DisplayName("AI 요청 중 오류가 발생하면 앞선 6개 progress 뒤 failed를 전달하고 연결을 종료한다")
    void SSE_실제분석실패_종료() throws Exception {
        // given
        Long runId = saveRun(snapshotJson());
        CustomException original = new CustomException(OpenApiErrorCode.OPEN_API_REQUEST_FAILED);
        doThrow(original).when(openApiUtil).request(any(OpenApiPrompt.class), eq(AnnualSalaryAnalyzationAiResDto.class));
        try (AnalyzationSseTestClient stream = new AnalyzationSseTestClient(port, runId, jwt.createAccessToken(memberId), null)) {
            stream.nextData();
            // when
            assertThatThrownBy(() -> service.analyze(runId)).isSameAs(original);
            // then
            for (int count = 1; count <= 6; count++) assertThat(stream.nextData().name()).isEqualTo("progress");
            AnalyzationSseTestClient.Event failed = stream.nextData();
            assertThat(failed.name()).isEqualTo("failed");
            assertThat(jsonMapper.readTree(failed.data()).get("steps").get(6).get("status").asString()).isEqualTo("FAILED");
            stream.awaitClosed();
        }
    }

    @Test
    @DisplayName("취소와 AI 오류가 경합하면 FAILED로 덮어쓰지 않고 canceled를 전달한다")
    void SSE_취소와분석오류경합_취소유지() throws Exception {
        // given
        Long runId = saveRun(snapshotJson());
        CustomException original = new CustomException(OpenApiErrorCode.OPEN_API_REQUEST_FAILED);
        doAnswer(invocation -> {
            stopRun(runId);
            throw original;
        }).when(openApiUtil).request(any(OpenApiPrompt.class), eq(AnnualSalaryAnalyzationAiResDto.class));
        try (AnalyzationSseTestClient stream = new AnalyzationSseTestClient(port, runId, jwt.createAccessToken(memberId), null)) {
            stream.nextData();
            // when
            assertThatThrownBy(() -> service.analyze(runId)).isSameAs(original);
            // then
            for (int count = 1; count <= 6; count++) assertThat(stream.nextData().name()).isEqualTo("progress");
            AnalyzationSseTestClient.Event canceled = stream.nextData();
            assertThat(canceled.name()).isEqualTo("canceled");
            assertThat(jsonMapper.readTree(canceled.data()).get("steps").get(6).get("status").asString()).isEqualTo("CANCELED");
            assertThat(events.findAllByRun_IdOrderBySortOrderAsc(runId)).noneMatch(event -> event.getStatus() == SpecProfileAnalyzationStatus.FAILED);
            stream.awaitClosed();
        }
    }

    @Test
    @DisplayName("동일 실행의 결과 저장이 동시에 요청되어도 한 번만 저장하고 완료한다")
    void 동시결과저장_중복방지_성공() throws Exception {
        // given
        SpecProfileAnnualSalaryAnalyzationResDto report = service.analyze(saveRun(snapshotJson()));
        Long runId = saveRun(snapshotJson());
        assertThat(stateService.startIfQueued(runId)).isTrue();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return resultSaveService.saveAndComplete(runId, report);
            };
            // when
            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);
            // then
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).hasSize(3);
        SpecProfileAnalyzationRun completed = runs.findById(runId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
        assertThat(results.findAllByRun_IdOrderByAnnualAsc(runId)).allSatisfy(saved -> {
            assertThat(saved.getSummary()).isEqualTo(report.summary());
            assertThat(saved.getComparisonGroupDescription()).isEqualTo(report.comparisonGroup().description());
            assertThat(saved.getRecommendation()).isEqualTo(report.recommendation());
        });
    }

    @Test
    @DisplayName("스냅샷 조회가 실패하면 FAILED 상태를 별도 트랜잭션으로 저장한다")
    void 손상스냅샷_조회실패_FAILED저장() {
        // given
        Long runId = saveRun("{");
        // when / then
        assertThatThrownBy(() -> service.analyze(runId)).isInstanceOf(CustomException.class);
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.FAILED);
    }

    @Test
    @DisplayName("첫 상태 점유 단계가 실패해 QUEUED로 남아도 FAILED로 보상한다")
    void 시작단계예외_FAILED저장() {
        // given
        Long runId = saveRun(snapshotJson());
        IllegalStateException failure = new IllegalStateException("상태 점유 실패");
        doThrow(failure).when(stateService).startIfQueued(runId);
        // when / then
        assertThatThrownBy(() -> service.analyze(runId)).isSameAs(failure);
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.FAILED);
    }

    @Test
    @DisplayName("OpenAI 실패와 호출자 롤백 이후에도 FAILED 상태를 유지한다")
    void 외부호출실패_호출자롤백_FAILED저장() {
        // given
        Long runId = saveRun(snapshotJson());
        doThrow(new CustomException(OpenApiErrorCode.OPEN_API_TIMEOUT)).when(openApiUtil)
                .request(any(OpenApiPrompt.class), eq(AnnualSalaryAnalyzationAiResDto.class));
        // when / then
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> service.analyze(runId)))
                .isInstanceOf(CustomException.class);
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.FAILED);
    }

    @Test
    @DisplayName("GPT의 연차 누락은 응답 검증에서 거절하고 실행을 FAILED로 저장한다")
    void 응답검증실패_FAILED저장() {
        // given
        Long runId = saveRun(snapshotJson());
        doAnswer(invocation -> new AnnualSalaryAnalyzationAiResDto("요약", List.of(), "집단", "보완점"))
                .when(openApiUtil).request(any(OpenApiPrompt.class), eq(AnnualSalaryAnalyzationAiResDto.class));
        // when / then
        assertThatThrownBy(() -> service.analyze(runId)).isInstanceOf(CustomException.class);
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.FAILED);
    }

    @Test
    @DisplayName("실패 상태 저장까지 실패하더라도 분석의 원래 예외를 유지한다")
    void 실패보상오류_원인예외유지() {
        // given
        Long runId = saveRun(snapshotJson());
        CustomException original = new CustomException(OpenApiErrorCode.OPEN_API_REQUEST_FAILED);
        doThrow(original).when(openApiUtil).request(any(OpenApiPrompt.class), eq(AnnualSalaryAnalyzationAiResDto.class));
        doThrow(new IllegalStateException("상태 저장 실패")).when(stateService).failIfRunning(runId);
        // when / then
        assertThatThrownBy(() -> service.analyze(runId)).isSameAs(original).hasSuppressedException(new IllegalStateException("상태 저장 실패"));
    }

    @Test
    @DisplayName("동일 QUEUED 실행을 동시에 시작해도 한 요청만 상태를 전환한다")
    void 동시상태전환_하나만성공() throws Exception {
        // given
        Long runId = saveRun(snapshotJson());
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return stateService.startIfQueued(runId);
            };
            // when
            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);
            // then
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    /** 새 트랜잭션에서 결과와 컬렉션을 조회해 분석 당시 값의 영속성을 검증한다. */
    private void assertStoredReport(Long runId, SpecProfileAnnualSalaryAnalyzationResDto report) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<SpecProfileAnalyzationResult> storedResults = results.findAllByRun_IdOrderByAnnualAsc(runId);
            assertThat(storedResults).hasSize(report.salaryResults().size());
            for (int index = 0; index < storedResults.size(); index++) {
                SpecProfileAnalyzationResult stored = storedResults.get(index);
                KeepSalaryMatchedSampleStatistics statistics = report.statistics();
                assertThat(stored.getJobSubCategoryCode()).isEqualTo(report.comparisonGroup().jobSubCategoryCode());
                assertThat(stored.getJobSubCategoryName()).isEqualTo(report.comparisonGroup().jobSubCategoryName());
                assertThat(stored.getObservationYears()).containsExactlyElementsOf(report.comparisonGroup().observationYears());
                assertThat(stored.getSampleCount()).isEqualTo(statistics.sampleCount());
                assertThat(stored.getAnnualSalaryMinKrw()).isEqualByComparingTo(statistics.annualSalaryMinKrw());
                assertThat(stored.getAnnualSalaryMaxKrw()).isEqualByComparingTo(statistics.annualSalaryMaxKrw());
                assertThat(stored.getAnnualSalaryMeanKrw()).isEqualByComparingTo(statistics.annualSalaryMeanKrw());
                assertThat(stored.getAnnualSalaryModesKrw()).usingElementComparator(BigDecimal::compareTo)
                        .containsExactlyElementsOf(statistics.annualSalaryModesKrw());
                assertThat(stored.getModeFrequency()).isEqualTo(statistics.modeFrequency());
                assertThat(stored.getAnnualSalaryP25Krw()).isEqualByComparingTo(statistics.annualSalaryP25Krw());
                assertThat(stored.getAnnualSalaryMedianKrw()).isEqualByComparingTo(statistics.annualSalaryMedianKrw());
                assertThat(stored.getAnnualSalaryP75Krw()).isEqualByComparingTo(statistics.annualSalaryP75Krw());
                assertThat(stored.getTopPercent()).isEqualTo(report.salaryResults().get(index).topPercent());
                assertThat(stored.getSalaryNotice()).isEqualTo(report.salaryNotice());
            }
        });
    }

    /** 실제 HTTP로 본인 실행의 중단을 요청하고 공통 성공 응답을 확인한다. @param runId 실행 ID */
    private void stopRun(Long runId) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/spec-profile/analyzation/runs/" + runId + "/stop"))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + jwt.createAccessToken(memberId))
                    .method("PATCH", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(response.body()).get("data").get("status").asString()).isEqualTo("CANCELED");
        }
    }

    private Long saveRun(String json) {
        return runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(profile).job(job)
                .createdAt(LocalDateTime.now()).status(SpecProfileAnalyzationStatus.QUEUED)
                .specProfileSnapshotJson(json).build()).getId();
    }

    private AnnualSalaryAnalyzationAiResDto aiResponse() {
        return new AnnualSalaryAnalyzationAiResDto("스펙 기반 AI 추정", List.of(
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(0, 34000000L, 36000000L, 38000000L),
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(3, 41000000L, 44500000L, 48000000L),
                new AnnualSalaryAnalyzationAiResDto.SalaryPrediction(5, 50000000L, 54500000L, 60000000L)), "누적 이력 기반 표본", "실무 경험 보완");
    }

    private String snapshotJson() {
        return jsonMapper.writeValueAsString(SpecProfileAnalyzationSnapshot.builder().specProfileId(profile.getId())
                .jobId(job.getId()).academicAbilities(List.of(
                        AcademicAbilityResDto.builder().type(AcademicAbilityType.UNIVERSITY).location(ResidentialArea.SEOUL)
                                .majorAffiliation(MajorMainField.ENGINEERING).majorName("컴퓨터공학과")
                                .universityGrade(new BigDecimal("3.75")).universityStandardGrade(new BigDecimal("4.5"))
                                .graduatedYearMonth(YearMonth.of(2024, 2)).build(),
                        AcademicAbilityResDto.builder().type(AcademicAbilityType.UNIVERSITY).location(ResidentialArea.BUSAN)
                                .majorAffiliation(MajorMainField.SOCIAL_SCIENCES).majorName("경제학")
                                .universityGrade(new BigDecimal("95")).universityStandardGrade(new BigDecimal("100")).build()))
                .languageExamScores(List.of(
                        LanguageExamScoreResDto.builder().languageExamId(1L).languageExamName("TOEIC").scoreNumeric(new BigDecimal("875")).build(),
                        LanguageExamScoreResDto.builder().languageExamId(5L).languageExamName("OPIC").scoreGrade("IH").build()))
                .certificates(List.of(CertificateResDto.builder().certificateId(1L).build(), CertificateResDto.builder().certificateId(2L).build()))
                .experiences(List.of()).build());
    }
}
