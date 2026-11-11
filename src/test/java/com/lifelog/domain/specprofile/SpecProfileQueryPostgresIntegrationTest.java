package com.lifelog.domain.specprofile;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.languageexam.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 실제 Flyway 스키마와 PostgreSQL 스냅샷에서 조회 계약을 검증한다. */
@EnabledIfEnvironmentVariable(named = "SPEC_PROFILE_TEST_DB_URL", matches = ".+")
class SpecProfileQueryPostgresIntegrationTest extends SpecProfileQueryIntegrationTest {
    private static final String SCHEMA = "spec_query_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;

    @Test
    @DisplayName("다른 트랜잭션의 임시 저장이 커밋되어도 진행 중인 조회 스냅샷은 유지된다")
    void 동시저장_조회스냅샷_성공() throws Exception {
        // given
        Job job = jobs.saveAndFlush(Job.builder().code("1000").name("개발").mainCategoryName("개발").build());
        exams.saveAndFlush(LanguageExam.builder().id(1L).name("TOEIC")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.NUMERIC).build());
        service.saveTemporary(member.getId(), saveExperienceRequest(job.getId(), "이전"));
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        CountDownLatch snapshotStarted = new CountDownLatch(1);
        CountDownLatch saved = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // when
            Future<?> reader = executor.submit(() -> read.executeWithoutResult(status -> {
                assertThat(service.getCurrentProfile(member.getId()).experiences().getFirst().title()).isEqualTo("이전");
                snapshotStarted.countDown();
                await(saved);
                entityManager.clear();
                // then: 1차 캐시를 비워도 동일 DB 스냅샷을 읽는다.
                assertThat(service.getCurrentProfile(member.getId()).experiences().getFirst().title()).isEqualTo("이전");
            }));
            Future<?> writer = executor.submit(() -> {
                await(snapshotStarted);
                try {
                    service.saveTemporary(member.getId(), saveExperienceRequest(job.getId(), "최신"));
                } finally {
                    saved.countDown();
                }
            });
            writer.get(30, TimeUnit.SECONDS);
            reader.get(30, TimeUnit.SECONDS);
        }
        assertThat(service.getCurrentProfile(member.getId()).experiences().getFirst().title()).isEqualTo("최신");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("트랜잭션 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
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
        try (var connection = DriverManager.getConnection(System.getenv("SPEC_PROFILE_TEST_DB_URL"),
                System.getenv("SPEC_PROFILE_TEST_DB_USER"), System.getenv("SPEC_PROFILE_TEST_DB_PASSWORD"));
             var sql = connection.createStatement()) {
            sql.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
}
