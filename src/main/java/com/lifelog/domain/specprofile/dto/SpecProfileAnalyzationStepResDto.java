package com.lifelog.domain.specprofile.dto;

import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import lombok.Builder;

@Builder
public record SpecProfileAnalyzationStepResDto(SpecProfileAnalyzationStep code, int order, String name) {}
