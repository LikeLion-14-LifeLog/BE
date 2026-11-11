package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.time.YearMonth;
import com.lifelog.domain.specprofile.enums.ExperienceType;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExperienceResDto(
    Long experienceId,
    ExperienceType type,
    String title,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
    @Schema(type = "string", example = "2025-02", nullable = true)
    YearMonth startedYearMonth,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
    @Schema(type = "string", example = "2025-02", nullable = true)
    YearMonth endedYearMonth,
    String description,
    boolean awarded,
    String organizerName,
    String awardGrade
) {}
