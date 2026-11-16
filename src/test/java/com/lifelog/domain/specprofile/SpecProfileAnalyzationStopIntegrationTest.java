package com.lifelog.domain.specprofile;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationEventLog;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationEventLogRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationResultRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.service.SpecProfileAnnualSalaryResultSaveService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationRunStateService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationSseRegistry;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:analysis-stop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileAnalyzationStopIntegrationTest {
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired JobRepository jobs;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @Autowired SpecProfileAnalyzationEventLogRepository events;
    @Autowired SpecProfileAnalyzationResultRepository results;
    @Autowired SpecProfileAnalyzationRunStateService state;
    @Autowired SpecProfileAnnualSalaryResultSaveService resultSave;
    @MockitoSpyBean SpecProfileAnalyzationSseRegistry registry;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    Member member;
    SpecProfile profile;
    SpecProfileAnalyzationRun run;

    @BeforeEach
    void 준비() {
        member = members.saveAndFlush(new Member(UUID.randomUUID() + "@stop.example"));
        profile = profiles.saveAndFlush(SpecProfile.builder().member(member).build());
        Job job = jobs.saveAndFlush(Job.builder().code("1332").name("개발자").mainCategoryName("연구직").build());
        run = runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(profile).job(job)
                .createdAt(LocalDateTime.now()).status(SpecProfileAnalyzationStatus.QUEUED).specProfileSnapshotJson("{}").build());
        events.saveAndFlush(SpecProfileAnalyzationEventLog.builder().run(run).sortOrder(0)
                .status(SpecProfileAnalyzationStatus.QUEUED).createdAt(run.getCreatedAt()).build());
    }

    @AfterEach
    void 정리() {
        registry.shutdown();
        for (String table : List.of("spec_profile_analyzation_result_salary_mode", "spec_profile_analyzation_result_observation_year",
                "spec_profile_analyzation_result", "spec_profile_analyzation_event_log", "spec_profile_analyzation_run",
                "spec_profile", "job", "member")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"QUEUED", "IN_PROGRESS"})
    @DisplayName("대기·진행 중인 본인 실행을 중단하고 상태 조회에도 취소를 반영한다")
    void API_본인실행중단_성공(SpecProfileAnalyzationStatus status) throws Exception {
        // given
        if (status == SpecProfileAnalyzationStatus.IN_PROGRESS) {
            state.startIfQueued(run.getId());
            registry.progress(run.getId(), SpecProfileAnalyzationStep.RUN_START);
        }
        // when
        MvcResult response = stop(member.getId());
        // then
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(response.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("code").asString()).isEqualTo("COMMON_200");
        assertThat(body.get("message").asString()).isEqualTo("요청이 성공적으로 처리되었습니다.");
        JsonNode data = body.get("data");
        assertThat(data.get("runId").asLong()).isEqualTo(run.getId());
        assertThat(data.get("specProfileId").asLong()).isEqualTo(profile.getId());
        assertThat(data.get("status").asString()).isEqualTo("CANCELED");
        OffsetDateTime canceledAt = OffsetDateTime.parse(data.get("canceledAt").asString());
        assertThat(canceledAt.getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        SpecProfileAnalyzationRun saved = runs.findById(run.getId()).orElseThrow();
        assertThat(canceledAt.toInstant()).isEqualTo(saved.getCanceledAt().atZone(ZoneId.systemDefault()).toInstant());
        assertThat(saved.getSpecProfileSnapshotJson()).isEqualTo("{}");
        assertThat(results.findAllByRun_IdOrderByAnnualAsc(run.getId())).isEmpty();
        List<SpecProfileAnalyzationEventLog> logs = events.findAllByRun_IdOrderBySortOrderAsc(run.getId());
        assertThat(logs.stream().filter(event -> event.getStep() == null && event.getStatus() == SpecProfileAnalyzationStatus.CANCELED).count()).isEqualTo(1);
        if (status == SpecProfileAnalyzationStatus.IN_PROGRESS) {
            assertThat(logs).anyMatch(event -> event.getStep() == SpecProfileAnalyzationStep.RUN_START && event.getStatus() == SpecProfileAnalyzationStatus.COMPLETED)
                    .anyMatch(event -> event.getStep() == SpecProfileAnalyzationStep.SNAPSHOT_LOAD && event.getStatus() == SpecProfileAnalyzationStatus.CANCELED);
        }
        mvc.perform(get("/api/spec-profile/analyzation/runs/{runId}", run.getId())
                        .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.result").isEmpty());
    }

    @Test
    @DisplayName("중단 재요청은 최초 응답의 시각과 이벤트 로그를 그대로 유지한다")
    void API_중단재요청_성공() throws Exception {
        // given
        String first = stop(member.getId()).getResponse().getContentAsString();
        List<Long> logIds = events.findAllByRun_IdOrderBySortOrderAsc(run.getId()).stream().map(SpecProfileAnalyzationEventLog::getId).toList();
        // when
        MvcResult repeated = stop(member.getId());
        // then
        assertThat(repeated.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(repeated.getResponse().getContentAsString())).isEqualTo(json.readTree(first));
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(run.getId()).stream().map(SpecProfileAnalyzationEventLog::getId).toList()).isEqualTo(logIds);
    }

    @ParameterizedTest
    @EnumSource(value = SpecProfileAnalyzationStatus.class, names = {"COMPLETED", "FAILED"})
    @DisplayName("완료·실패한 실행은 상태와 로그를 바꾸지 않고 중단을 거절한다")
    void API_종료실행중단_실패_상태충돌(SpecProfileAnalyzationStatus terminal) throws Exception {
        // given
        jdbc.update("UPDATE spec_profile_analyzation_run SET status=? WHERE run_id=?", terminal.name(), run.getId());
        // when
        MvcResult response = stop(member.getId());
        // then
        assertThat(response.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(response.getResponse().getContentAsString()).get("code").asString()).isEqualTo("RUN_NOT_STOPPABLE");
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(terminal);
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(run.getId())).hasSize(1);
    }

    @Test
    @DisplayName("타인 실행과 미존재 실행은 동일한 404 응답이며 인증 누락은 401이다")
    void API_소유권및인증_실패() throws Exception {
        // given
        Member other = members.saveAndFlush(new Member(UUID.randomUUID() + "@other.example"));
        // when / then
        MvcResult foreign = stop(other.getId());
        MvcResult missing = mvc.perform(patch("/api/spec-profile/analyzation/runs/{runId}/stop", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(json.readTree(foreign.getResponse().getContentAsString())).isEqualTo(json.readTree(missing.getResponse().getContentAsString()));
        assertThat(json.readTree(missing.getResponse().getContentAsString()).get("code").asString()).isEqualTo("RUN_NOT_FOUND");
        mvc.perform(patch("/api/spec-profile/analyzation/runs/{runId}/stop", run.getId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401"));
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.QUEUED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    @DisplayName("양수가 아니거나 int64로 변환할 수 없는 경로 변수는 400으로 반환한다")
    void API_잘못된실행ID_실패(String id) throws Exception {
        // given / when
        MvcResult response = mvc.perform(patch("/api/spec-profile/analyzation/runs/{runId}/stop", id)
                        .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
        // then
        assertThat(json.readTree(response.getResponse().getContentAsString()).get("code").asString())
                .isEqualTo(id.equals("0") || id.equals("-1") ? "INVALID_REQUEST" : "COMMON_400");
    }

    @Test
    @DisplayName("취소 이벤트 저장 실패는 실행 상태와 중단 시각까지 함께 롤백한다")
    void API_취소로그저장실패_전체롤백() throws Exception {
        // given
        String constraint = "ck_stop_failure_" + run.getId();
        jdbc.execute("ALTER TABLE spec_profile_analyzation_event_log ADD CONSTRAINT " + constraint
                + " CHECK (spec_profile_analyzation_run_id <> " + run.getId() + " OR status <> 'CANCELED')");
        try {
            // when
            assertThat(stop(member.getId()).getResponse().getStatus()).isEqualTo(500);
            // then
            SpecProfileAnalyzationRun unchanged = runs.findById(run.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(SpecProfileAnalyzationStatus.QUEUED);
            assertThat(unchanged.getCanceledAt()).isNull();
            assertThat(events.findAllByRun_IdOrderBySortOrderAsc(run.getId())).hasSize(1);
        } finally {
            jdbc.execute("ALTER TABLE spec_profile_analyzation_event_log DROP CONSTRAINT " + constraint);
        }
    }

    @Test
    @DisplayName("커밋 후 SSE 통지 실패는 이미 확정된 중단의 성공 응답을 변경하지 않는다")
    void API_취소통지실패_중단유지_성공() throws Exception {
        // given: 전송 경계만 실패시키고 상태·로그 저장은 실제 서비스를 사용한다.
        doThrow(new IllegalStateException("SSE 통지 실패")).when(registry).canceled(run.getId());
        // when
        assertThat(stop(member.getId()).getResponse().getStatus()).isEqualTo(200);
        // then
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.CANCELED);
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(run.getId())).anyMatch(event ->
                event.getStep() == null && event.getStatus() == SpecProfileAnalyzationStatus.CANCELED);
    }

    @Test
    @DisplayName("동시 중단은 같은 최초 시각을 반환하고 취소 로그를 한 번만 저장한다")
    void 동시중단_중복방지_성공() throws Exception {
        // given / when
        RaceResult<MvcResult, MvcResult> responses = race(() -> stop(member.getId()), () -> stop(member.getId()));
        // then
        assertThat(responses.first().getResponse().getStatus()).isEqualTo(200);
        assertThat(responses.second().getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(responses.first().getResponse().getContentAsString()))
                .isEqualTo(json.readTree(responses.second().getResponse().getContentAsString()));
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(run.getId())).hasSize(2);
    }

    @Test
    @DisplayName("실행 시작과 중단이 경합해도 최종 취소 상태를 덮어쓰지 않는다")
    void 동시시작과중단_취소유지_성공() throws Exception {
        // given / when
        RaceResult<MvcResult, Boolean> responses = race(() -> stop(member.getId()), () -> state.startIfQueued(run.getId()));
        // then
        assertThat(responses.first().getResponse().getStatus()).isEqualTo(200);
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.CANCELED);
        assertThat(state.startIfQueued(run.getId())).isFalse();
        assertThat(state.failIfRunning(run.getId())).isFalse();
    }

    @Test
    @DisplayName("결과 저장과 중단이 경합하면 하나의 종료 상태와 일치하는 결과만 남긴다")
    void 동시결과저장과중단_일관성_성공() throws Exception {
        // given
        state.startIfQueued(run.getId());
        // when
        RaceResult<MvcResult, Boolean> responses = race(() -> stop(member.getId()), () -> resultSave.saveAndComplete(run.getId(), report()));
        // then
        SpecProfileAnalyzationRun saved = runs.findById(run.getId()).orElseThrow();
        if (saved.getStatus() == SpecProfileAnalyzationStatus.CANCELED) {
            assertThat(responses.first().getResponse().getStatus()).isEqualTo(200);
            assertThat(responses.second()).isFalse();
            assertThat(results.findAllByRun_IdOrderByAnnualAsc(run.getId())).isEmpty();
        } else {
            assertThat(saved.getStatus()).isEqualTo(SpecProfileAnalyzationStatus.COMPLETED);
            assertThat(responses.first().getResponse().getStatus()).isEqualTo(409);
            assertThat(responses.second()).isTrue();
            assertThat(results.findAllByRun_IdOrderByAnnualAsc(run.getId())).hasSize(3);
            assertThat(saved.getCanceledAt()).isNull();
        }
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(run.getId()).stream()
                .filter(event -> event.getStep() == null && !event.getStatus().isRunning()).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 처리와 중단이 경합하면 먼저 커밋한 종료 상태를 유지한다")
    void 동시실패와중단_일관성_성공() throws Exception {
        // given
        state.startIfQueued(run.getId());
        // when
        RaceResult<MvcResult, Boolean> responses = race(() -> stop(member.getId()), () -> state.failIfRunning(run.getId()));
        // then
        SpecProfileAnalyzationStatus terminal = runs.findById(run.getId()).orElseThrow().getStatus();
        if (terminal == SpecProfileAnalyzationStatus.CANCELED) {
            assertThat(responses.first().getResponse().getStatus()).isEqualTo(200);
            assertThat(responses.second()).isFalse();
        } else {
            assertThat(terminal).isEqualTo(SpecProfileAnalyzationStatus.FAILED);
            assertThat(responses.first().getResponse().getStatus()).isEqualTo(409);
            assertThat(responses.second()).isTrue();
        }
        assertThat(events.findAllByRun_IdOrderBySortOrderAsc(run.getId()).stream()
                .filter(event -> event.getStep() == null && !event.getStatus().isRunning()).count()).isEqualTo(1);
    }

    /** 인증된 실제 중단 API를 호출한다. @param memberId 인증 회원 @return HTTP 응답 */
    private MvcResult stop(Long memberId) throws Exception {
        return mvc.perform(patch("/api/spec-profile/analyzation/runs/{runId}/stop", run.getId())
                .header("Authorization", "Bearer " + jwt.createAccessToken(memberId))).andReturn();
    }

    /** 서로 다른 스레드와 트랜잭션에서 두 요청을 동시에 시작한다. */
    private <T, U> RaceResult<T, U> race(Callable<T> first, Callable<U> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> firstResult = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return first.call(); });
            Future<U> secondResult = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return second.call(); });
            return new RaceResult<>(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        }
    }

    /** 결과 저장과 중단의 경합에 사용할 완전한 보고서를 생성한다. */
    private SpecProfileAnnualSalaryAnalyzationResDto report() {
        BigDecimal salary = new BigDecimal("36000000");
        return SpecProfileAnnualSalaryAnalyzationResDto.builder().summary("분석 요약").recommendation("스펙 보완").salaryNotice("AI 추정")
                .salaryResults(List.of(0, 3, 5).stream().map(annual -> SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult.builder()
                        .annual(annual).annualIncomeMin(34000000L).annualIncomeExpected(36000000L).annualIncomeMax(38000000L).build()).toList())
                .comparisonGroup(SpecProfileAnnualSalaryAnalyzationResDto.ComparisonGroup.builder().jobSubCategoryCode("133")
                        .jobSubCategoryName("개발직").sampleCount(10).observationYears(List.of(2024)).description("비교 표본").build())
                .statistics(new KeepSalaryMatchedSampleStatistics(10, salary, salary, salary, List.of(salary), 10, salary, salary, salary)).build();
    }

    private record RaceResult<T, U>(T first, U second) {}
}
