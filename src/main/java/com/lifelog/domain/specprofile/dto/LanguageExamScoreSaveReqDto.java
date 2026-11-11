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

public record LanguageExamScoreSaveReqDto(
    @Schema(description = "기존 어학 성적 PK. 신규 등록 시 생략 또는 null", nullable = true, example = "null")
    @Positive Long languageExamScoreId,
    @Schema(description = "등록된 어학 시험 PK. 예시는 NUMERIC 유형인 TOEIC", example = "1")
    @NotNull @Positive Long languageExamId,
    @Schema(description = "숫자 점수. NUMERIC 및 NUMERIC_AND_GRADE에서 필수", example = "865")
    @Digits(integer = 5, fraction = 1) BigDecimal scoreNumeric,
    @Schema(description = "등급. GRADE 및 NUMERIC_AND_GRADE에서 필수, NUMERIC이면 null", nullable = true)
    @Size(max = 20) String scoreGrade,
    @Schema(description = "성적 취득 연월. 미래 월 불가", type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2024-08")
    @NotNull YearMonth acquiredYearMonth,
    @Schema(description = "성적 만료 연월. 유효기간이 없으면 null", type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", nullable = true, example = "2026-08")
    YearMonth expiresYearMonth
) {
}

