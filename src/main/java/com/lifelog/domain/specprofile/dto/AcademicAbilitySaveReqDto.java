package com.lifelog.domain.specprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.major.enums.MajorMainField;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record AcademicAbilitySaveReqDto(
    @Schema(description = "기존 학력 PK. 신규 등록 시 생략 또는 null", nullable = true, example = "null")
    @Positive Long academicAbilityId,
    @Schema(description = "학력 유형", example = "UNIVERSITY")
    @NotNull AcademicAbilityType type,
    @Schema(description = "학적 상태", example = "GRADUATED")
    @NotNull AcademicAbilityStatus status,
    @Schema(description = "학교 소재지", example = "SEOUL")
    @NotNull ResidentialArea location,
    @Schema(description = "대학 평점. 만점 기준과 함께 입력", example = "3.75")
    @Digits(integer = 1, fraction = 2) BigDecimal universityGrade,
    @Schema(description = "평점 만점 기준", example = "4.5")
    BigDecimal universityStandardGrade,
    @Schema(description = "졸업 또는 졸업예정 연월", type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2025-02")
    @NotNull YearMonth graduatedYearMonth,
    @Schema(description = "전공계열. 참조 전공의 계열과 일치해야 함", example = "SOCIAL_SCIENCES")
    @NotNull MajorMainField majorAffiliation,
    @Schema(description = "등록된 전공 PK", example = "10")
    @NotNull @Positive Long majorId
) {
}

