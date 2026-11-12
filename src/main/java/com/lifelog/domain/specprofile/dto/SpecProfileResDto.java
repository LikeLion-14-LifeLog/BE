package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.time.OffsetDateTime;
import java.util.List;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileResDto(
    Long specProfileId,
    SpecProfileType type,
    Integer birthYear,
    ResidentialArea residenceRegion,
    @Schema(description = "DesiredJob PK. 저장 요청의 Job PK와 다름", nullable = true)
    Long desiredJobId,
    Long jobId,
    String desiredJobName,
    List<AcademicAbilityResDto> academicAbilities,
    List<CertificateResDto> certificates,
    List<LanguageExamScoreResDto> languageExamScores,
    List<ExperienceResDto> experiences,
    @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime updatedAt
) {}
