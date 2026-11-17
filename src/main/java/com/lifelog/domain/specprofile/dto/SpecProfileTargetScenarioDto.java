package com.lifelog.domain.specprofile.dto;

import lombok.Builder;

/** 요청 안에서만 유지하며 DB에 저장하지 않는 비교 시나리오. */
@Builder
public record SpecProfileTargetScenarioDto(
        String title,
        SpecProfileTargetChanges changes,
        SpecProfileAnalyzationSnapshot snapshot,
        SpecProfileSimulationAnalysis analysis
) {}
