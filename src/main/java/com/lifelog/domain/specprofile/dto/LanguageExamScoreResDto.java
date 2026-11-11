package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.time.YearMonth;
import java.math.BigDecimal;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LanguageExamScoreResDto(
    Long languageExamScoreId,
    Long languageExamId,
    String languageExamName,
    BigDecimal scoreNumeric,
    String scoreGrade,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
    @Schema(type = "string", example = "2025-02", nullable = true)
    YearMonth acquiredYearMonth,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
    @Schema(type = "string", example = "2025-02", nullable = true)
    YearMonth expiresYearMonth
) {}
