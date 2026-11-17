package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.SpecProfileTargetChanges;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetResDto;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Flyway 스키마에서 목표 조회와 동시 저장의 스냅샷 일관성을 검증한다. */
@EnabledIfEnvironmentVariable(named = "SPEC_PROFILE_TEST_DB_URL", matches = ".+")
class SpecProfileTargetQueryPostgresIntegrationTest extends SpecProfileTargetQueryIntegrationTest {
    private static final String SCHEMA = "target_query_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;

    @Test
    @DisplayName("다른 목표 저장이 커밋되어도 진행 중인 조회의 조건과 하위 항목은 같은 스냅샷이다")
    void 동시목표저장_조회스냅샷유지_성공() throws Exception {
        // given
        save(fullChanges(), CompanyGroupScale.LARGE);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch saved = new CountDownLatch(1);
        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // when
            Future<?> reader = executor.submit(() -> read.executeWithoutResult(status -> {
                SpecProfileTargetResDto previous = targetQuery.getSelectedTarget(member.getId());
                started.countDown();
                await(saved);
                entityManager.clear();
                assertThat(targetQuery.getSelectedTarget(member.getId())).isEqualTo(previous);
            }));
            Future<?> writer = executor.submit(() -> {
                await(started);
                try {
                    save(SpecProfileTargetChanges.empty(), CompanyGroupScale.SMALL);
                } finally {
                    saved.countDown();
                }
            });
            writer.get(30, TimeUnit.SECONDS);
            reader.get(30, TimeUnit.SECONDS);
        }
        // then
        SpecProfileTargetResDto latest = targetQuery.getSelectedTarget(member.getId());
        assertThat(latest.companyGroupScale()).isEqualTo(CompanyGroupScale.SMALL);
        assertThat(latest.changes().academicAbilities()).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("조회 스냅샷 대기 시간 초과");
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
