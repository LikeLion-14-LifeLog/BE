package com.lifelog.domain.specprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualSalaryResultMigrationTest {
    @Test
    @DisplayName("통계 이관은 과거 결과를 보존하고 소수 통계·여러 최빈값·관측 연도를 저장하며 삭제 시 함께 정리한다")
    void 통계컬럼이관_기존데이터보존과컬렉션저장_성공() throws Exception {
        // given
        String url = "jdbc:h2:mem:statistics-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL";
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE spec_profile_analyzation_result (result_id BIGINT PRIMARY KEY, annual INTEGER, annual_income_min BIGINT, annual_income_expected BIGINT, annual_income_max BIGINT)");
            statement.execute("INSERT INTO spec_profile_analyzation_result VALUES (1, 0, 34000000, 36000000, 38000000)");
            // when
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V20261113_1__add_annual_salary_result_ai_fields.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V20261113_2__persist_annual_salary_result_statistics.sql"));
            // then
            try (ResultSet rows = statement.executeQuery("SELECT * FROM spec_profile_analyzation_result WHERE result_id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("annual_income_expected")).isEqualTo(36000000L);
                assertThat(rows.getObject("sample_count")).isNull();
                assertThat(rows.getBigDecimal("annual_salary_mean_krw")).isNull();
                assertThat(rows.getBigDecimal("top_percent")).isNull();
            }
            statement.execute("UPDATE spec_profile_analyzation_result SET sample_count=120, annual_salary_min_krw=20000000.25, annual_salary_max_krw=60000000.75, annual_salary_mean_krw=36000000.38, mode_frequency=8, annual_salary_p25_krw=30000000.13, annual_salary_median_krw=35000000.25, annual_salary_p75_krw=42000000.63, top_percent=28.0, job_sub_category_code='133', job_sub_category_name='개발직', salary_notice='분석 당시 기준' WHERE result_id=1");
            statement.execute("INSERT INTO spec_profile_analyzation_result_salary_mode VALUES (1,0,35000000.25),(1,1,40000000.75)");
            statement.execute("INSERT INTO spec_profile_analyzation_result_observation_year VALUES (1,0,2021),(1,1,2024)");
            try (ResultSet rows = statement.executeQuery("SELECT * FROM spec_profile_analyzation_result WHERE result_id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("sample_count")).isEqualTo(120);
                assertThat(rows.getBigDecimal("annual_salary_mean_krw")).isEqualByComparingTo(new BigDecimal("36000000.38"));
                assertThat(rows.getBigDecimal("annual_salary_p25_krw")).isEqualByComparingTo(new BigDecimal("30000000.13"));
                assertThat(rows.getBigDecimal("top_percent")).isEqualByComparingTo("28.0");
                assertThat(rows.getString("salary_notice")).isEqualTo("분석 당시 기준");
            }
            try (ResultSet rows = statement.executeQuery("SELECT annual_salary_krw FROM spec_profile_analyzation_result_salary_mode WHERE result_id=1 ORDER BY sort_order")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("35000000.25");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("40000000.75");
                assertThat(rows.next()).isFalse();
            }
            try (ResultSet rows = statement.executeQuery("SELECT observation_year FROM spec_profile_analyzation_result_observation_year WHERE result_id=1 ORDER BY sort_order")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2021);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2024);
                assertThat(rows.next()).isFalse();
            }
            statement.execute("DELETE FROM spec_profile_analyzation_result WHERE result_id=1");
            try (ResultSet rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM spec_profile_analyzation_result_salary_mode) + (SELECT COUNT(*) FROM spec_profile_analyzation_result_observation_year)")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    @Test
    @DisplayName("기존 연봉 결과를 보존하고 AI 문구 세 컬럼에 긴 한글을 손실 없이 저장한다")
    void AI응답컬럼이관_기존데이터와TEXT보존_성공() throws Exception {
        // given
        String url = "jdbc:h2:mem:result-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL";
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE spec_profile_analyzation_run (run_id BIGINT PRIMARY KEY, status VARCHAR(20))");
            statement.execute("INSERT INTO spec_profile_analyzation_run VALUES (1, 'COMPLETED')");
            statement.execute("CREATE TABLE spec_profile_analyzation_result (result_id BIGINT PRIMARY KEY, annual INTEGER, annual_income_min BIGINT, annual_income_expected BIGINT, annual_income_max BIGINT)");
            statement.execute("INSERT INTO spec_profile_analyzation_result VALUES (1, 0, 34000000, 36000000, 38000000)");
            // when
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V20261113_1__add_annual_salary_result_ai_fields.sql"));
            // then
            try (ResultSet rows = statement.executeQuery("SELECT * FROM spec_profile_analyzation_result WHERE result_id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("annual_income_expected")).isEqualTo(36000000L);
                assertThat(rows.getString("summary")).isNull();
                assertThat(rows.getString("comparison_group_description")).isNull();
                assertThat(rows.getString("recommendation")).isNull();
            }
            String text = "한글 연봉 분석 결과 ".repeat(5000);
            try (PreparedStatement update = connection.prepareStatement("UPDATE spec_profile_analyzation_result SET summary = ?, comparison_group_description = ?, recommendation = ? WHERE result_id = 1")) {
                update.setString(1, text);
                update.setString(2, text + "비교 집단");
                update.setString(3, text + "보완점");
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            try (ResultSet rows = statement.executeQuery("SELECT summary, comparison_group_description, recommendation FROM spec_profile_analyzation_result WHERE result_id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(text);
                assertThat(rows.getString(2)).isEqualTo(text + "비교 집단");
                assertThat(rows.getString(3)).isEqualTo(text + "보완점");
            }
            try (ResultSet columns = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SPEC_PROFILE_ANALYZATION_RUN' AND column_name = 'ANNUAL_SALARY_REPORT_JSON'")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt(1)).isZero();
            }
        }
    }
}
