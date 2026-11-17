package com.lifelog.domain.specprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpecProfileTargetStorageMigrationTest {
    @Test
    @DisplayName("목표 스펙 저장 이관은 기존 값을 보존하면서 미정 취득일과 100점 학점을 허용한다")
    void 목표저장스키마변경_기존데이터보존_성공() throws Exception {
        // given
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:target-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE spec_profile_certificate (id BIGINT, acquired_at DATE NOT NULL)");
            statement.execute("CREATE TABLE language_exam_score (id BIGINT, acquired_at DATE NOT NULL)");
            statement.execute("CREATE TABLE academic_ability (id BIGINT, university_grade NUMERIC(3,2))");
            statement.execute("INSERT INTO academic_ability VALUES (1,3.75)");
            statement.execute("INSERT INTO spec_profile_certificate VALUES (1,'2024-01-01')");
            // when
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V20261117_3__support_target_spec_storage.sql"));
            statement.execute("INSERT INTO spec_profile_certificate VALUES (2,NULL)");
            statement.execute("INSERT INTO language_exam_score VALUES (2,NULL)");
            statement.execute("INSERT INTO academic_ability VALUES (2,100.00)");
            // then
            try (ResultSet rows = statement.executeQuery("SELECT university_grade FROM academic_ability ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("3.75");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("100.00");
            }
            try (ResultSet rows = statement.executeQuery("SELECT acquired_at FROM spec_profile_certificate ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDate(1).toLocalDate()).hasToString("2024-01-01");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDate(1)).isNull();
            }
        }
    }
}
