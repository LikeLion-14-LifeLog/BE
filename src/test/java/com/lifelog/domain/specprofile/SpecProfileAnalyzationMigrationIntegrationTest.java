package com.lifelog.domain.specprofile;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SPEC_PROFILE_TEST_DB_URL", matches = ".+")
class SpecProfileAnalyzationMigrationIntegrationTest {
    @Test
    @DisplayName("ERD 컬럼과 실행 PK 및 이벤트 FK, identity와 제약을 검증한다")
    void ERD이관_관계및제약_성공() throws Exception {
        // given
        String schema = newSchema();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try {
                flyway(schema, "20261110.4").migrate();
                statement.execute("SET search_path TO " + schema);
                statement.execute("INSERT INTO member(member_id,email) VALUES (1,'migration@example.com')");
                statement.execute("INSERT INTO spec_profile(spec_profile_id,member_id) VALUES (1,1)");
                statement.execute("INSERT INTO job(job_id,code,name,main_category_name) VALUES (1,'0001','개발','개발')");
                // when
                flyway(schema, "latest").migrate();
                // then
                try (ResultSet columns = statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema='"
                        + schema + "' AND column_name IN ('model_version','percentile')")) {
                    assertThat(columns.next()).isFalse();
                }
                long runId;
                try (ResultSet run = statement.executeQuery("INSERT INTO spec_profile_analyzation_run(spec_profile_id,job_id,spec_profile_snapshot_json) VALUES (1,1,'{}') RETURNING run_id,delete_at")) {
                    assertThat(run.next()).isTrue();
                    runId = run.getLong("run_id");
                    assertThat(run.getObject("delete_at")).isNull();
                }
                statement.execute("INSERT INTO spec_profile_analyzation_event_log(spec_profile_analyzation_run_id,status) VALUES (" + runId + ",'QUEUED')");
                assertThatThrownBy(() -> statement.execute("INSERT INTO spec_profile_analyzation_event_log(spec_profile_analyzation_run_id,status) VALUES (" + runId + ",'QUEUED')"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("UPDATE spec_profile_analyzation_run SET status='QUEUE' WHERE run_id=" + runId))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("UPDATE spec_profile_analyzation_run SET status='CANCELED' WHERE run_id=" + runId))
                        .isInstanceOf(SQLException.class);
                statement.execute("INSERT INTO spec_profile_analyzation_result(spec_profile_analyzation_run_id,annual,annual_income_min,annual_income_expected,annual_income_max) VALUES ("
                        + runId + ",40000,1,2,3)");
                statement.execute("DELETE FROM spec_profile_analyzation_run WHERE run_id=" + runId);
                try (ResultSet rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM spec_profile_analyzation_result) + (SELECT COUNT(*) FROM spec_profile_analyzation_event_log)")) {
                    rows.next();
                    assertThat(rows.getLong(1)).isZero();
                }
            } finally {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test
    @DisplayName("과거 실행이 있으면 임의 스냅샷을 생성하지 않고 기존 데이터와 컬럼을 보존한다")
    void 과거실행존재_이관중단_성공() throws Exception {
        // given
        String schema = newSchema();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try {
                flyway(schema, "20261110.4").migrate();
                statement.execute("SET search_path TO " + schema);
                statement.execute("INSERT INTO member(member_id,email) VALUES (1,'legacy@example.com')");
                statement.execute("INSERT INTO spec_profile(spec_profile_id,member_id) VALUES (1,1)");
                statement.execute("INSERT INTO spec_profile_analyzation_run(spec_profile_id,model_version) VALUES (1,'legacy')");
                // when / then
                assertThatThrownBy(() -> flyway(schema, "latest").migrate()).isInstanceOf(RuntimeException.class);
                try (ResultSet rows = statement.executeQuery("SELECT spec_profile_analyzation_run_id,model_version FROM spec_profile_analyzation_run")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("model_version")).isEqualTo("legacy");
                }
            } finally {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private String newSchema() {
        return "analysis_migration_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("SPEC_PROFILE_TEST_DB_URL"),
                System.getenv("SPEC_PROFILE_TEST_DB_USER"), System.getenv("SPEC_PROFILE_TEST_DB_PASSWORD"));
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(System.getenv("SPEC_PROFILE_TEST_DB_URL"),
                System.getenv("SPEC_PROFILE_TEST_DB_USER"), System.getenv("SPEC_PROFILE_TEST_DB_PASSWORD"))
                .schemas(schema).target(target).load();
    }
}
