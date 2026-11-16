package com.lifelog.domain.specprofile.dto;

import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import lombok.Builder;

@Builder
public record SpecProfileAnalyzationProgressStepResDto(
        int order, String code, String name, SpecProfileAnalyzationStatus status
) {}
