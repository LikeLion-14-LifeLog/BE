package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.YearMonth;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.major.enums.MajorMainField;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AcademicAbilityResDto(
    Long academicAbilityId,
    AcademicAbilityType type,
    AcademicAbilityStatus status,
    ResidentialArea location,
    BigDecimal universityGrade,
    BigDecimal universityStandardGrade,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
    @Schema(type = "string", example = "2025-02", nullable = true)
    YearMonth graduatedYearMonth,
    MajorMainField majorAffiliation,
    Long majorId,
    String majorName
) {}
