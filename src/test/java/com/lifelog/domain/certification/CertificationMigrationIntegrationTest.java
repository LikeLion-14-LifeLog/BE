package com.lifelog.domain.certification;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@EnabledIfEnvironmentVariable(named = "CERTIFICATION_TEST_DB_URL", matches = ".+")
class CertificationMigrationIntegrationTest {
    @Test @DisplayName("PostgreSQL에서 기존 PK·FK와 부분 UNIQUE 인덱스를 유지한다")
    void 기존스키마_이관_성공() throws Exception {
        // given: 운영 DB와 분리된 테스트 DB의 독립 스키마
        String url = System.getenv("CERTIFICATION_TEST_DB_URL");
        String user = System.getenv("CERTIFICATION_TEST_DB_USER");
        String password = System.getenv("CERTIFICATION_TEST_DB_PASSWORD");
        String schema = "cert_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            try {
                Flyway.configure().dataSource(url,user,password).schemas(schema).target("20261109.4").load().migrate();
                sql.execute("SET search_path TO " + schema);
                sql.execute("INSERT INTO certificate(certificate_id,type,main_category,name,code,issuer) VALUES (100,'PRIVATE','A','기존','A01011-30','기관')");
                sql.execute("INSERT INTO member(member_id,email) VALUES (1,'test@example.com')");
                sql.execute("INSERT INTO spec_profile(spec_profile_id,member_id) VALUES (1,1)");
                sql.execute("INSERT INTO spec_profile_certificate(spec_profile_id,certificate_id,acquired_at) VALUES (1,100,CURRENT_DATE)");
                // when
                var flyway = Flyway.configure().dataSource(url,user,password).schemas(schema).load();
                flyway.migrate();
                // then
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("20261110.1");
                try (var result = sql.executeQuery("SELECT c.main_category,c.authorized_institution_name FROM certificate c JOIN spec_profile_certificate s ON c.certificate_id=s.certificate_id WHERE c.certificate_id=100")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("COMMON");
                    assertThat(result.getString(2)).isEqualTo("기관");
                }
                sql.execute("INSERT INTO certificate(type,main_category,name) VALUES ('PRIVATE','COMMON','코드없음')");
                assertThatThrownBy(() -> sql.execute("INSERT INTO certificate(type,main_category,name) VALUES ('PRIVATE','COMMON','코드없음')"))
                    .isInstanceOf(java.sql.SQLException.class);
            } finally {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
