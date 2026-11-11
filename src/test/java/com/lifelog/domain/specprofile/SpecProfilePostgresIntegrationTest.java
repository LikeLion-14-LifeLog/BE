package com.lifelog.domain.specprofile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.lifelog.domain.auth.dto.SignupRequest;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.enums.CertificationType;
import com.lifelog.domain.certification.enums.CertificationMainCategory;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.entity.SpecProfileCertificate;
import com.lifelog.domain.specprofile.entity.LanguageExamScore;
import com.lifelog.domain.specprofile.dto.CertificateSaveReqDto;
import com.lifelog.domain.specprofile.dto.LanguageExamScoreSaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import java.util.UUID;

/** 동일 저장 시나리오를 실제 Flyway 스키마와 PostgreSQL 잠금으로 검증한다. */
@EnabledIfEnvironmentVariable(named = "SPEC_PROFILE_TEST_DB_URL", matches = ".+")
class SpecProfilePostgresIntegrationTest extends SpecProfileSaveIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    private static final String SCHEMA = "spec_app_" + UUID.randomUUID().toString().replace("-", "");

    @Test
    @DisplayName("CURRENT 삽입 실패 시 회원과 인증 정보도 함께 롤백된다")
    void 회원가입_실패_프로필생성롤백() {
        // given: 기존 행은 유지하고 새 프로필 INSERT만 실패시키는 실제 DB 제약
        long memberCount = members.count();
        long identityCount = identities.count();
        jdbc.execute("ALTER TABLE spec_profile ADD CONSTRAINT reject_new_profile CHECK (birth_year IS NOT NULL) NOT VALID");
        try {
            // when / then
            assertThatThrownBy(() -> authService.signup(new SignupRequest("rollback@example.com", "password123")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(members.existsByEmail("rollback@example.com")).isFalse();
            assertThat(members.count()).isEqualTo(memberCount);
            assertThat(identities.count()).isEqualTo(identityCount);
        } finally {
            jdbc.execute("ALTER TABLE spec_profile DROP CONSTRAINT reject_new_profile");
        }
    }

    @Test
    @DisplayName("자격증과 시험 참조를 서로 교환해도 기존 행 PK를 유지하며 커밋한다")
    void 자격증및시험_참조교환_성공() {
        // given
        Certification secondCertificate = certifications.saveAndFlush(Certification.builder().name("두 번째 자격증")
                .type(CertificationType.PRIVATE).mainCategory(CertificationMainCategory.COMMON).build());
        LanguageExam secondExam = exams.saveAndFlush(LanguageExam.builder().id(2L).name("두 번째 숫자시험")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.NUMERIC).build());
        YearMonth month = YearMonth.of(2024, 1);
        service.saveTemporary(member.getId(), new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU,
                job.getId(), null,
                List.of(new CertificateSaveReqDto(null, certificate.getId(), month),
                        new CertificateSaveReqDto(null, secondCertificate.getId(), month)),
                List.of(new LanguageExamScoreSaveReqDto(null, exam.getId(), new BigDecimal("700"), null, month, null),
                        new LanguageExamScoreSaveReqDto(null, secondExam.getId(), new BigDecimal("800"), null, month, null)),
                List.of()));
        List<SpecProfileCertificate> originalCertificates = certificates.findAllBySpecProfileId(profile.getId());
        Long firstCertificateId = originalCertificates.stream()
                .filter(row -> row.getCertificate().getId().equals(certificate.getId())).findFirst().orElseThrow().getId();
        Long secondCertificateId = originalCertificates.stream()
                .filter(row -> row.getCertificate().getId().equals(secondCertificate.getId())).findFirst().orElseThrow().getId();
        List<LanguageExamScore> originalScores = scores.findAllBySpecProfileId(profile.getId());
        Long firstScoreId = originalScores.stream()
                .filter(row -> row.getLanguageExam().getId().equals(exam.getId())).findFirst().orElseThrow().getId();
        Long secondScoreId = originalScores.stream()
                .filter(row -> row.getLanguageExam().getId().equals(secondExam.getId())).findFirst().orElseThrow().getId();
        // when
        service.saveTemporary(member.getId(), new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU,
                job.getId(), null,
                List.of(new CertificateSaveReqDto(firstCertificateId, secondCertificate.getId(), month),
                        new CertificateSaveReqDto(secondCertificateId, certificate.getId(), month)),
                List.of(new LanguageExamScoreSaveReqDto(firstScoreId, secondExam.getId(), new BigDecimal("750"), null, month, null),
                        new LanguageExamScoreSaveReqDto(secondScoreId, exam.getId(), new BigDecimal("850"), null, month, null)),
                List.of()));
        // then
        assertThat(certificates.findById(firstCertificateId).orElseThrow().getCertificate().getId()).isEqualTo(secondCertificate.getId());
        assertThat(certificates.findById(secondCertificateId).orElseThrow().getCertificate().getId()).isEqualTo(certificate.getId());
        assertThat(scores.findById(firstScoreId).orElseThrow().getLanguageExam().getId()).isEqualTo(secondExam.getId());
        assertThat(scores.findById(secondScoreId).orElseThrow().getLanguageExam().getId()).isEqualTo(exam.getId());
        assertThat(certificates.count()).isEqualTo(2);
        assertThat(scores.count()).isEqualTo(2);
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
             Statement sql = connection.createStatement()) {
            sql.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
}

