package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
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

/** 사용자 목표와 AI 대안에 공통으로 사용하는 추가 스펙. 미래 날짜와 미정 날짜를 허용한다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileTargetChanges(
        @Schema(description = "추가할 학력. majorId는 등록된 실제 전공 ID로 지정")
        List<@NotNull @Valid AcademicAbilityChange> academicAbilities,
        @Schema(description = "추가할 자격증. 현재 보유하지 않은 실제 자격증 ID로 지정")
        List<@NotNull @Valid CertificateChange> certificates,
        @Schema(description = "추가할 어학 시험. 현재 등록하지 않은 시험 ID와 해당 시험 유형에 맞는 점수로 지정")
        List<@NotNull @Valid LanguageExamScoreChange> languageExamScores,
        @Schema(description = "추가할 경험. 미래 시작·종료 연월을 허용하며 별도 참조 ID가 필요 없음")
        List<@NotNull @Valid ExperienceChange> experiences
) {
    public SpecProfileTargetChanges {
        academicAbilities = academicAbilities == null ? List.of() : List.copyOf(academicAbilities);
        certificates = certificates == null ? List.of() : List.copyOf(certificates);
        languageExamScores = languageExamScores == null ? List.of() : List.copyOf(languageExamScores);
        experiences = experiences == null ? List.of() : List.copyOf(experiences);
    }

    /** 추가 항목이 없는 목표를 생성한다. */
    public static SpecProfileTargetChanges empty() {
        return new SpecProfileTargetChanges(List.of(), List.of(), List.of(), List.of());
    }

    /** 현재 스펙에 추가할 항목이 있는지 반환한다. */
    public boolean hasChanges() {
        return !academicAbilities.isEmpty() || !certificates.isEmpty()
                || !languageExamScores.isEmpty() || !experiences.isEmpty();
    }

    @Schema(description = "추가 학력 예시. majorId와 majorAffiliation은 조회한 실제 전공 정보로 교체", example = """
            {
              "type": "GRADUATE_SCHOOL_MASTER",
              "status": "ATTENDING",
              "location": "SEOUL",
              "universityGrade": 4.1,
              "universityStandardGrade": 4.5,
              "graduatedYearMonth": "2029-02",
              "majorAffiliation": "ENGINEERING",
              "majorId": 32592
            }
            """)
    public record AcademicAbilityChange(
            @NotNull AcademicAbilityType type,
            @NotNull AcademicAbilityStatus status,
            @NotNull ResidentialArea location,
            @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal universityGrade,
            BigDecimal universityStandardGrade,
            @Schema(type = "string", example = "2027-02", nullable = true) YearMonth graduatedYearMonth,
            @NotNull MajorMainField majorAffiliation,
            @NotNull @Positive Long majorId
    ) {}

    @Schema(description = "추가 자격증 예시. certificateId는 실제 미보유 자격증 ID로 교체", example = """
            {
              "certificateId": 2,
              "acquiredYearMonth": null
            }
            """)
    public record CertificateChange(
            @NotNull @Positive Long certificateId,
            @Schema(type = "string", example = "2027-02", nullable = true) YearMonth acquiredYearMonth
    ) {}

    @Schema(description = "등급형 시험 예시. 시험 ID와 점수 유형을 확인하고 현재 보유 시험과 중복되지 않게 지정", example = """
            {
              "languageExamId": 7,
              "scoreNumeric": null,
              "scoreGrade": "IH",
              "acquiredYearMonth": null,
              "expiresYearMonth": null
            }
            """)
    public record LanguageExamScoreChange(
            @NotNull @Positive Long languageExamId,
            @DecimalMin("0") @Digits(integer = 5, fraction = 1) BigDecimal scoreNumeric,
            @Size(max = 20) String scoreGrade,
            @Schema(type = "string", example = "2027-02", nullable = true) YearMonth acquiredYearMonth,
            @Schema(type = "string", example = "2029-02", nullable = true) YearMonth expiresYearMonth
    ) {}

    @Schema(description = "추가 경험 예시. 미수상이면 organizerName과 awardGrade는 null", example = """
            {
              "type": "INTERNSHIP",
              "title": "백엔드 개발 인턴",
              "startedYearMonth": "2027-01",
              "endedYearMonth": "2027-06",
              "description": "Spring Boot 기반 API 개발 및 서비스 운영 경험",
              "awarded": false,
              "organizerName": null,
              "awardGrade": null
            }
            """)
    public record ExperienceChange(
            @NotNull ExperienceType type,
            @NotBlank @Size(max = 255) String title,
            @NotNull @Schema(type = "string", example = "2027-01") YearMonth startedYearMonth,
            @Schema(type = "string", example = "2027-06", nullable = true) YearMonth endedYearMonth,
            @Size(max = 300) String description,
            @NotNull Boolean awarded,
            @Size(max = 200) String organizerName,
            @Size(max = 50) String awardGrade
    ) {}
}
