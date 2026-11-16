package com.lifelog.domain.specprofile;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationEventLog;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationEventLogRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationProgressQueryService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationProgressService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationRunStateService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationSseRegistry;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:analysis-sse;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.jpa.open-in-view=false", "spec-profile.analyzation.sse.timeout-ms=5000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileAnalyzationSseIntegrationTest {
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired JobRepository jobs;
    @Autowired SpecProfileAnalyzationRunRepository runs;
    @Autowired SpecProfileAnalyzationEventLogRepository events;
    @Autowired SpecProfileAnalyzationRunStateService state;
    @Autowired SpecProfileAnalyzationSseRegistry registry;
    @Autowired SpecProfileAnalyzationProgressService progressService;
    @MockitoSpyBean SpecProfileAnalyzationProgressQueryService queries;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @LocalServerPort int port;
    Member member;
    SpecProfileAnalyzationRun run;
    Long initialEventId;
    String token;

    @BeforeEach
    void 준비() {
        member = members.saveAndFlush(new Member(UUID.randomUUID() + "@sse.example"));
        SpecProfile profile = profiles.saveAndFlush(SpecProfile.builder().member(member).build());
        Job job = jobs.saveAndFlush(Job.builder().code("1332").name("개발자").mainCategoryName("연구직").build());
        run = runs.saveAndFlush(SpecProfileAnalyzationRun.builder().specProfile(profile).job(job)
                .createdAt(LocalDateTime.now()).status(SpecProfileAnalyzationStatus.QUEUED).specProfileSnapshotJson("{}").build());
        initialEventId = events.saveAndFlush(SpecProfileAnalyzationEventLog.builder().run(run).sortOrder(0)
                .createdAt(run.getCreatedAt()).status(SpecProfileAnalyzationStatus.QUEUED).build()).getId();
        token = jwt.createAccessToken(member.getId());
    }

    @AfterEach
    void 정리() {
        registry.shutdown();
        jdbc.update("DELETE FROM spec_profile_analyzation_run");
        jdbc.update("DELETE FROM spec_profile");
        jdbc.update("DELETE FROM job");
        jdbc.update("DELETE FROM member");
    }

    @Test
    @DisplayName("snapshot과 단계 완료 progress, heartbeat를 전달하고 재접속은 최신 snapshot으로 동기화한다")
    void 구독_진행및재접속_성공() throws Exception {
        // given
        try (AnalyzationSseTestClient first = connect(null)) {
            AnalyzationSseTestClient.Event snapshot = first.nextData();
            assertThat(snapshot.name()).isEqualTo("snapshot");
            assertThat(snapshot.id()).isEqualTo(initialEventId.toString());
            assertThat(json.readTree(snapshot.data()).get("totalSteps").asInt()).isEqualTo(9);
            // when
            assertThat(state.startIfQueued(run.getId())).isTrue();
            assertThat(registry.progress(run.getId(), SpecProfileAnalyzationStep.RUN_START)).isTrue();
            AnalyzationSseTestClient.Event progress = first.nextData();
            JsonNode data = json.readTree(progress.data());
            // then
            assertThat(progress.name()).isEqualTo("progress");
            assertThat(data.get("status").asString()).isEqualTo("IN_PROGRESS");
            assertThat(data.get("completedStepCount").asInt()).isEqualTo(1);
            assertThat(data.get("currentStepOrder").asInt()).isEqualTo(2);
            assertThat(data.get("steps").get(0).get("status").asString()).isEqualTo("COMPLETED");
            assertThat(data.get("steps").get(1).get("name").asString()).isEqualTo(SpecProfileAnalyzationStep.SNAPSHOT_LOAD.getDisplayName());
            assertThat(progress.id()).isEqualTo(data.get("eventLogId").asString());
            registry.heartbeat();
            assertThat(first.next().name()).isEqualTo("heartbeat");
            try (AnalyzationSseTestClient second = connect(snapshot.id())) {
                AnalyzationSseTestClient.Event resynchronized = second.nextData();
                assertThat(resynchronized.name()).isEqualTo("snapshot");
                assertThat(resynchronized.id()).isEqualTo(progress.id());
                registry.progress(run.getId(), SpecProfileAnalyzationStep.SNAPSHOT_LOAD);
                assertThat(first.nextData().name()).isEqualTo("progress");
                assertThat(second.nextData().name()).isEqualTo("progress");
                registry.failed(run.getId());
                assertThat(first.nextData().name()).isEqualTo("failed");
                assertThat(second.nextData().name()).isEqualTo("failed");
                first.awaitClosed();
                second.awaitClosed();
            }
        }
    }

    @Test
    @DisplayName("실패 시 완료한 단계와 미시작 단계를 보존하고 진행 단계만 FAILED로 표시한다")
    void 단계실패_종료_성공() throws Exception {
        // given
        try (AnalyzationSseTestClient stream = connect(null)) {
            stream.nextData();
            state.startIfQueued(run.getId());
            registry.progress(run.getId(), SpecProfileAnalyzationStep.RUN_START);
            stream.nextData();
            // when
            registry.failed(run.getId());
            // then
            AnalyzationSseTestClient.Event failed = stream.nextData();
            JsonNode data = json.readTree(failed.data());
            assertThat(failed.name()).isEqualTo("failed");
            assertThat(data.get("currentStepOrder").isNull()).isTrue();
            assertThat(data.get("steps").get(0).get("status").asString()).isEqualTo("COMPLETED");
            assertThat(data.get("steps").get(1).get("status").asString()).isEqualTo("FAILED");
            assertThat(data.get("steps").get(2).get("status").asString()).isEqualTo("QUEUED");
            assertThat(data.get("resultUrl").isNull()).isTrue();
            stream.awaitClosed();
        }
    }

    @Test
    @DisplayName("대기 실행의 확정된 취소는 전체 단계를 QUEUED로 남기고 canceled 후 연결을 종료한다")
    void 대기실행취소_성공() throws Exception {
        // given
        try (AnalyzationSseTestClient stream = connect(null)) {
            stream.nextData();
            // when
            jdbc.update("UPDATE spec_profile_analyzation_run SET status='CANCELED', canceled_at=CURRENT_TIMESTAMP WHERE run_id=?", run.getId());
            registry.canceled(run.getId());
            // then
            AnalyzationSseTestClient.Event canceled = stream.nextData();
            JsonNode data = json.readTree(canceled.data());
            assertThat(canceled.name()).isEqualTo("canceled");
            assertThat(data.get("completedStepCount").asInt()).isZero();
            for (JsonNode step : data.get("steps")) assertThat(step.get("status").asString()).isEqualTo("QUEUED");
            stream.awaitClosed();
        }
    }

    @Test
    @DisplayName("과거 완료 실행은 모르는 단계 상태를 만들지 않고 snapshot과 completed를 전송한다")
    void 과거완료실행_구독_성공() throws Exception {
        // given
        jdbc.update("UPDATE spec_profile_analyzation_run SET status='COMPLETED' WHERE run_id=?", run.getId());
        // when
        try (AnalyzationSseTestClient stream = connect(null)) {
            AnalyzationSseTestClient.Event snapshot = stream.nextData();
            // then
            JsonNode data = json.readTree(snapshot.data());
            assertThat(snapshot.name()).isEqualTo("snapshot");
            assertThat(data.get("completedStepCount").isNull()).isTrue();
            assertThat(data.get("steps").get(0).get("status").isNull()).isTrue();
            assertThat(data.get("resultUrl").asString()).endsWith("/runs/" + run.getId());
            assertThat(stream.nextData().name()).isEqualTo("completed");
            stream.awaitClosed();
        }
    }

    @Test
    @DisplayName("구독 초기화와 진행이 경합해도 최신 snapshot이 먼저 오고 이전 progress가 중복되지 않는다")
    void 구독초기화_진행경합_성공() throws Exception {
        // given
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch continueSnapshot = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            if (reads.incrementAndGet() == 2) {
                registered.countDown();
                assertThat(continueSnapshot.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.callRealMethod();
        }).when(queries).getOwnedSnapshot(eq(member.getId()), eq(run.getId()), any());
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<AnalyzationSseTestClient> connected = executor.submit(() -> connect(null));
            try {
                assertThat(registered.await(5, TimeUnit.SECONDS)).isTrue();
                // when
                state.startIfQueued(run.getId());
                registry.progress(run.getId(), SpecProfileAnalyzationStep.RUN_START);
            } finally {
                continueSnapshot.countDown();
            }
            try (AnalyzationSseTestClient stream = connected.get(10, TimeUnit.SECONDS)) {
                // then
                AnalyzationSseTestClient.Event snapshot = stream.nextData();
                assertThat(snapshot.name()).isEqualTo("snapshot");
                assertThat(json.readTree(snapshot.data()).get("completedStepCount").asInt()).isEqualTo(1);
                registry.progress(run.getId(), SpecProfileAnalyzationStep.SNAPSHOT_LOAD);
                AnalyzationSseTestClient.Event progress = stream.nextData();
                assertThat(progress.name()).isEqualTo("progress");
                assertThat(json.readTree(progress.data()).get("completedStepCount").asInt()).isEqualTo(2);
            }
        }
    }

    @Test
    @DisplayName("연결 timeout 후에도 실행을 취소하지 않고 후속 분석을 계속 처리한다")
    void 연결시간초과_분석상태유지_성공() throws Exception {
        // given
        try (AnalyzationSseTestClient stream = connect(null)) {
            stream.nextData();
            // when
            stream.awaitClosed();
            // then
            assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.QUEUED);
            assertThat(state.startIfQueued(run.getId())).isTrue();
            assertThat(registry.progress(run.getId(), SpecProfileAnalyzationStep.RUN_START)).isTrue();
        }
    }

    @Test
    @DisplayName("전송 풀 포화로 연결이 종료돼도 단계 저장과 분석 처리를 실패시키지 않는다")
    void 전송예약실패_분석진행유지_성공() {
        // given: 전송 풀 경계만 실패시키고 저장·상태 전환은 실제 서비스를 사용한다.
        SpecProfileAnalyzationSseRegistry saturated = new SpecProfileAnalyzationSseRegistry(progressService, queries, state,
                task -> { throw new RejectedExecutionException("전송 풀 포화"); }, 5000);
        // when
        saturated.subscribe(member.getId(), run.getId(), null);
        state.startIfQueued(run.getId());
        // then
        assertThat(saturated.progress(run.getId(), SpecProfileAnalyzationStep.RUN_START)).isTrue();
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(SpecProfileAnalyzationStatus.IN_PROGRESS);
        saturated.shutdown();
    }

    @Test
    @DisplayName("구독은 JWT 인증과 실행 소유권을 검증하며 SSE Accept 요청의 오류도 JSON으로 반환한다")
    void 인증및소유권_실패() throws Exception {
        // given / when / then
        mvc.perform(get(url())).andExpect(status().isUnauthorized());
        Member other = members.saveAndFlush(new Member("other@sse.example"));
        mvc.perform(get(url()).accept(MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer " + jwt.createAccessToken(other.getId())))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
        mvc.perform(get("/api/spec-profile/analyzation/runs/9223372036854775807/events").accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "101:3", "9223372036854775808", "9223372036854775807"})
    @DisplayName("잘못된 Last-Event-ID를 JSON 400으로 거절한다")
    void 커서검증_실패(String cursor) throws Exception {
        // given / when / then
        mvc.perform(get(url()).accept(MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer " + token).header("Last-Event-ID", cursor))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("존재하는 이벤트 PK라도 다른 실행의 커서이면 거절한다")
    void 다른실행커서_실패() throws Exception {
        // given
        SpecProfileAnalyzationRun other = runs.saveAndFlush(SpecProfileAnalyzationRun.builder()
                .specProfile(run.getSpecProfile()).job(run.getJob()).createdAt(LocalDateTime.now())
                .status(SpecProfileAnalyzationStatus.QUEUED).specProfileSnapshotJson("{}").build());
        Long foreignCursor = events.saveAndFlush(SpecProfileAnalyzationEventLog.builder().run(other)
                .sortOrder(0).createdAt(LocalDateTime.now()).status(SpecProfileAnalyzationStatus.QUEUED).build()).getId();
        // when / then
        mvc.perform(get(url()).accept(MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer " + token)
                        .header("Last-Event-ID", foreignCursor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    @DisplayName("실행 ID는 양의 int64이며 잘못된 형식은 스트림 시작 전에 거절한다")
    void 실행ID검증_실패(String id) throws Exception {
        // given / when / then
        mvc.perform(get("/api/spec-profile/analyzation/runs/" + id + "/events").accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private AnalyzationSseTestClient connect(String cursor) throws Exception {
        return new AnalyzationSseTestClient(port, run.getId(), token, cursor);
    }

    private String url() { return "/api/spec-profile/analyzation/runs/" + run.getId() + "/events"; }
}
