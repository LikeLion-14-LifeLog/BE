package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import lombok.Builder;

/** 특정 분석 실행의 상태와 완료된 분석 보고서. */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileAnalyzationRunResultResDto(
        Long runId,
        Long specProfileId,
        SpecProfileAnalyzationStatus status,
        SpecProfileAnnualSalaryAnalyzationResDto result
) {}
