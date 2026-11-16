package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record SpecProfileAnalyzationRunStopResDto(
        Long runId,
        Long specProfileId,
        SpecProfileAnalyzationStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime canceledAt
) {}
