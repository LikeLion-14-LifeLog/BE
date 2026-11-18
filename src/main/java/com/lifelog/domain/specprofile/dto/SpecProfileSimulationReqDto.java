package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import com.lifelog.domain.specprofile.enums.ExperienceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Schema(description = "현재 스펙에 일회성으로 추가할 목표 조건. DB에는 저장하지 않습니다.", example = """
        {
          "baseSpecProfileId": 10,
          "companyGroupScale": "LARGE",
          "changes": {
            "academicAbilities": [
              {
                "type": "GRADUATE_SCHOOL_MASTER",
                "status": "ATTENDING",
                "location": "DAEJEON",
                "universityGrade": 4.1,
                "universityStandardGrade": 4.3,
                "majorAffiliation": "ENGINEERING",
                "majorId": 32592
              }
            ],
            "certificates": [
              {
                "certificateId": 2
              }
            ],
            "languageExamScores": [
              {
                "languageExamId": 5,
                "scoreNumeric": null,
                "scoreGrade": "AL"
              }
            ],
            "experiences": [
              {
                "type": "PROJECT",
                "title": "채용 데이터 분석 서비스 개발",
                "startedYearMonth": "2026-09",
                "description": "채용 공고 수집과 연봉 통계 대시보드 개발",
                "awarded": false,
                "organizerName": null,
                "awardGrade": null
              }
            ]
          }
        }
        """)
public record SpecProfileSimulationReqDto(
        @Schema(description = "로그인 회원의 CURRENT 스펙 PK. 예시 10을 본인의 ID로 변경", example = "10")
        @NotNull @Positive Long baseSpecProfileId,
        @Schema(description = "예측 대상 기업 규모. 생략 또는 null이면 미지정", example = "LARGE")
        CompanyGroupScale companyGroupScale,
        @Schema(description = "현재 스펙에 추가할 항목. 자격증·시험 ID는 현재 보유 항목과 중복되지 않는 실제 ID로 지정")
        @Valid Changes changes
) {
    public record Changes(
            List<@NotNull @Valid AcademicAbilityChange> academicAbilities,
            List<@NotNull @Valid CertificateChange> certificates,
            List<@NotNull @Valid LanguageExamScoreChange> languageExamScores,
            List<@NotNull @Valid ExperienceChange> experiences
    ) {
        public Changes {
            academicAbilities = academicAbilities == null ? List.of() : List.copyOf(academicAbilities);
            certificates = certificates == null ? List.of() : List.copyOf(certificates);
            languageExamScores = languageExamScores == null ? List.of() : List.copyOf(languageExamScores);
            experiences = experiences == null ? List.of() : List.copyOf(experiences);
        }

        /** 추가할 목표 항목의 존재 여부를 반환한다. */
        public boolean hasChanges() {
            return !academicAbilities.isEmpty() || !certificates.isEmpty()
                    || !languageExamScores.isEmpty() || !experiences.isEmpty();
        }
    }

    @JsonIgnoreProperties("graduatedYearMonth")
    public record AcademicAbilityChange(
            @NotNull AcademicAbilityType type,
            @NotNull AcademicAbilityStatus status,
            @NotNull ResidentialArea location,
            @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal universityGrade,
            BigDecimal universityStandardGrade,
            @NotNull MajorMainField majorAffiliation,
            @NotNull @Positive Long majorId
    ) {}

    @JsonIgnoreProperties("acquiredYearMonth")
    public record CertificateChange(@NotNull @Positive Long certificateId) {}

    @JsonIgnoreProperties({"acquiredYearMonth", "expiresYearMonth"})
    public record LanguageExamScoreChange(
            @NotNull @Positive Long languageExamId,
            @DecimalMin("0") @Digits(integer = 5, fraction = 1) BigDecimal scoreNumeric,
            @Size(max = 20) String scoreGrade
    ) {}

    @JsonIgnoreProperties("endedYearMonth")
    public record ExperienceChange(
            @NotNull ExperienceType type,
            @NotBlank @Size(max = 255) String title,
            @Schema(description = "활동 시작 연월. 미래 월 허용", type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-09")
            @NotNull YearMonth startedYearMonth,
            @Size(max = 300) String description,
            @NotNull Boolean awarded,
            @Size(max = 200) String organizerName,
            @Size(max = 50) String awardGrade
    ) {}
}
