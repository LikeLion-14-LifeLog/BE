package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import lombok.Builder;
import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record SpecProfileAnalyzationRunResDto(
        Long runId,
        Long specProfileId,
        SpecProfileAnalyzationStatus status,
        int totalSteps,
        int completedStepCount,
        List<SpecProfileAnalyzationStepResDto> steps,
        String eventsUrl,
        String resultUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime requestedAt
) {}
