package com.lifelog.domain.specprofile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

/** 동일 저장·롤백·동시성 시나리오를 실제 PostgreSQL과 Flyway 스키마에서도 실행한다. */
@EnabledIfEnvironmentVariable(named = "SPEC_PROFILE_TEST_DB_URL", matches = ".+")
class SpecProfileTargetPostgresIntegrationTest extends SpecProfileTargetSaveIntegrationTest {
    private static final String SCHEMA = "spec_target_" + UUID.randomUUID().toString().replace("-", "");

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
