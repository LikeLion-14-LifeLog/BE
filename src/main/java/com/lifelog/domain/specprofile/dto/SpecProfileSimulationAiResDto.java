package com.lifelog.domain.specprofile.dto;

import lombok.Builder;

import java.util.List;

/** 기존 연봉 예측과 목표 조건의 준비 기간·비용·판단 기준을 포함하는 AI 응답. */
@Builder
public record SpecProfileSimulationAiResDto(
        String summary,
        List<AnnualSalaryAnalyzationAiResDto.SalaryPrediction> salaryResults,
        String comparisonGroupDescription,
        String recommendation,
        String preparationPeriod,
        String estimatedCost,
        String decisionCriteria
) {
    public static final int PREPARATION_PERIOD_MAX_LENGTH = 30;
    public static final int ESTIMATED_COST_MAX_LENGTH = 40;
    public static final int DECISION_CRITERIA_MAX_LENGTH = 60;
    public static final String SINGLE_LINE_PATTERN = "^[^\\r\\n]+$";
    public static final String POLITE_SENTENCE_PATTERN = "^[^\\r\\n]*요[.!?]?$";
}
