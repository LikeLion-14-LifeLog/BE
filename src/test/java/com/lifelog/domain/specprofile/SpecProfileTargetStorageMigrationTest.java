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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpecProfileTargetStorageMigrationTest {
    @Test
    @DisplayName("목표 조회 이관은 기존 데이터를 보존하고 기존 목표를 미기록 상태로 유지한다")
    void 조회구분정보이관_기존값보존과기본값_성공() throws Exception {
        // given
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:target-query-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL");
             Statement statement = connection.createStatement()) {
            List<String> tables = List.of("spec_profile", "academic_ability", "spec_profile_certificate", "language_exam_score", "experience");
            for (String table : tables) {
                statement.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, preserved VARCHAR(20))");
                statement.execute("INSERT INTO " + table + " VALUES (1,'기존 데이터')");
            }
            statement.execute("ALTER TABLE spec_profile ADD COLUMN member_id BIGINT");
            statement.execute("ALTER TABLE spec_profile ADD COLUMN type VARCHAR(20)");
            statement.execute("ALTER TABLE spec_profile ADD COLUMN target_spec_selected BOOLEAN");
            // when
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V20261117_4__support_selected_target_query.sql"));
            // then
            for (String table : tables) {
                statement.execute("INSERT INTO " + table + " (id,preserved) VALUES (2,'신규 데이터')");
                try (ResultSet rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    for (int index = 1; index <= 2; index++) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString("preserved")).isEqualTo(index == 1 ? "기존 데이터" : "신규 데이터");
                        assertThat(rows.getBoolean(table.equals("spec_profile") ? "target_conditions_recorded" : "target_additional")).isFalse();
                        assertThat(rows.wasNull()).isFalse();
                        if (table.equals("spec_profile")) assertThat(rows.getString("target_company_group_scale")).isNull();
                    }
                }
            }
        }
    }

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
