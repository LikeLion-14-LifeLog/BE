package com.lifelog.domain.dashboard;

import com.lifelog.domain.dashboard.dto.DashboardResDto;
import com.lifelog.domain.dashboard.mapper.DashboardMapper;
import com.lifelog.domain.jobposting.entity.JobPosting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** 실제 Flyway 스키마에서 대시보드 자체 트랜잭션의 조회 스냅샷을 검증한다. */
@EnabledIfEnvironmentVariable(named = "SPEC_PROFILE_TEST_DB_URL", matches = ".+")
class DashboardPostgresIntegrationTest extends DashboardIntegrationTest {
    private static final String SCHEMA = "dashboard_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean DashboardMapper mapper;

    @Test
    @DisplayName("분석 조회 도중 공고와 기술 요건이 변경되어도 동일 스냅샷의 대시보드를 반환한다")
    void 동시공고변경_대시보드스냅샷유지_성공() throws Exception {
        // given
        saveCompleted();
        JobPosting posting = savePosting("변경 전 공고", job, 5, null, "Java");
        CountDownLatch analysisRead = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        // 실제 매핑을 유지하면서 분석 조회와 공고 조회 사이의 실행 시점만 제어한다.
        doAnswer(invocation -> {
            DashboardResDto.Analysis analysis = (DashboardResDto.Analysis) invocation.callRealMethod();
            analysisRead.countDown();
            await(committed);
            return analysis;
        }).when(mapper).toAnalysisResDto(any(), any());
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            // when: 조회 스레드에는 외부 트랜잭션 없이 대시보드 서비스의 트랜잭션만 적용한다.
            Future<DashboardResDto> reader = executor.submit(() -> dashboard.getDashboard(member.getId()));
            try {
                await(analysisRead);
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.update("UPDATE job_posting SET title='변경 후 공고' WHERE job_posting_id=?", posting.getId());
                    jdbc.update("UPDATE job_posting_requirement SET content='Kotlin' WHERE job_posting_id=?", posting.getId());
                });
            } finally {
                committed.countDown();
            }
            // then
            DashboardResDto.JobPostingCard previous = reader.get(30, TimeUnit.SECONDS).data().recommendedJobPostings().jobPostings().getFirst();
            assertThat(previous.title()).isEqualTo("변경 전 공고");
            assertThat(previous.skillTags()).containsExactly("Java");
        }
        DashboardResDto.JobPostingCard latest = dashboard.getDashboard(member.getId()).data().recommendedJobPostings().jobPostings().getFirst();
        assertThat(latest.title()).isEqualTo("변경 후 공고");
        assertThat(latest.skillTags()).containsExactly("Kotlin");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("대시보드 조회 동기화 시간 초과");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        String url = System.getenv("SPEC_PROFILE_TEST_DB_URL");
        properties.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> System.getenv("SPEC_PROFILE_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("SPEC_PROFILE_TEST_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @AfterAll
    static void removeSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("SPEC_PROFILE_TEST_DB_URL"),
                System.getenv("SPEC_PROFILE_TEST_DB_USER"), System.getenv("SPEC_PROFILE_TEST_DB_PASSWORD"));
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
}
