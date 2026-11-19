package com.lifelog.domain.specprofile.dto;

import lombok.Builder;

@Builder
public record SpecProfileAnalyzationRunLatestResDto(Long runId) {
    public static SpecProfileAnalyzationRunLatestResDto create(Long runId) {
        return SpecProfileAnalyzationRunLatestResDto.builder().runId(runId).build();
    }
}
