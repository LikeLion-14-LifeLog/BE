package com.lifelog.domain.specprofile;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SPEC_PROFILE_TEST_DB_URL", matches = ".+")
class SpecProfileMigrationIntegrationTest {
    @Test
    @DisplayName("PostgreSQL에서 기존 데이터 보존, 기본값, 백필 및 최종 상태 UNIQUE를 검증한다")
    void 기존스키마_백필및제약변경_성공() throws Exception {
        // given
        String url = System.getenv("SPEC_PROFILE_TEST_DB_URL");
        String user = System.getenv("SPEC_PROFILE_TEST_DB_USER");
        String password = System.getenv("SPEC_PROFILE_TEST_DB_PASSWORD");
        String schema = "spec_test_" + UUID.randomUUID().toString().replace("-", "");
        var before = Flyway.configure().dataSource(url, user, password).schemas(schema).target("20261110.2").load();
        before.migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            sql.execute("INSERT INTO member(member_id,email) VALUES (1,'existing@example.com'),(2,'backfill@example.com')");
            sql.execute("INSERT INTO spec_profile(spec_profile_id,member_id,birth_year,residence_region) VALUES (100,1,1999,'DAEGU')");
            sql.execute("INSERT INTO language_exam(language_exam_id,name,language_type,score_type) VALUES (1,'시험1','ENGLISH','NUMERIC'),(2,'시험2','ENGLISH','NUMERIC')");
            sql.execute("INSERT INTO language_exam_score(spec_profile_id,language_exam_id,score_numeric,acquired_at) VALUES (100,1,100,'2024-01-01'),(100,2,200,'2024-01-01')");
            sql.execute("INSERT INTO certificate(certificate_id,type,main_category,name,code) VALUES (100,'PRIVATE','COMMON','자격증1','TEST1'),(200,'PRIVATE','COMMON','자격증2','TEST2')");
            sql.execute("INSERT INTO spec_profile_certificate(spec_profile_id,certificate_id,acquired_at) VALUES (100,100,'2024-01-01'),(100,200,'2024-01-01')");
            try {
                // when
                var latest =Flyway.configure().dataSource(url, user, password).schemas(schema).target("20261110.4").load();
                latest.migrate();
                // then
                assertThat(latest.info().current().getVersion().toString()).isEqualTo("20261112.6");
                try (var rows = sql.executeQuery("SELECT member_id,type,sort_order,birth_year FROM spec_profile ORDER BY member_id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("member_id")).isEqualTo(1);
                    assertThat(rows.getInt("birth_year")).isEqualTo(1999);
                    assertThat(rows.getInt("sort_order")).isZero();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("member_id")).isEqualTo(2);
                    assertThat(rows.getString("type")).isEqualTo("CURRENT");
                    assertThat(rows.getInt("sort_order")).isZero();
                    assertThat(rows.next()).isFalse();
                }
                sql.execute("INSERT INTO spec_profile(member_id,type,sort_order) VALUES (1,'TARGET',1),(1,'TARGET',2),(1,'TARGET',3)");
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM pg_indexes WHERE schemaname='" + schema + "' AND indexname='idx_spec_profile_member_type'")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
                // 각 UPDATE 중의 일시적 중복은 허용하고 커밋 시 최종 상태로 검사한다.
                connection.setAutoCommit(false);
                sql.execute("UPDATE language_exam_score SET language_exam_id=CASE language_exam_id WHEN 1 THEN 2 ELSE 1 END WHERE spec_profile_id=100");
                sql.execute("UPDATE spec_profile_certificate SET certificate_id=CASE certificate_id WHEN 100 THEN 200 ELSE 100 END WHERE spec_profile_id=100");
                connection.commit();
                connection.setAutoCommit(true);
                assertThatThrownBy(() -> sql.execute("INSERT INTO language_exam_score(spec_profile_id,language_exam_id,acquired_at) VALUES (100,1,'2024-01-01')"))
                        .isInstanceOf(SQLException.class);
                // 백필 SQL을 다시 적용해도 CURRENT가 중복되지 않는다.
                var backfill = new org.springframework.core.io.ClassPathResource("db/migration/V20261110_4__backfill_current_spec_profiles.sql");
                try (var input = backfill.getInputStream()) {
                    sql.execute(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM spec_profile WHERE type='CURRENT'")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            } finally {
                connection.setAutoCommit(true);
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}

