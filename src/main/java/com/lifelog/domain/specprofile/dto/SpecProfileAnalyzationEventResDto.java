package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import lombok.Builder;
import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record SpecProfileAnalyzationEventResDto(
        Long runId,
        Long eventLogId,
        SpecProfileAnalyzationStatus status,
        int totalSteps,
        Integer completedStepCount,
        Integer currentStepOrder,
        List<SpecProfileAnalyzationProgressStepResDto> steps,
        String resultUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime occurredAt
) {}
