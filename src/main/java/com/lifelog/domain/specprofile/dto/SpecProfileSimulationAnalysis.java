package com.lifelog.domain.specprofile.dto;

/** 서버 통계를 결합한 분석과 해당 목표의 준비 정보를 함께 전달한다. */
public record SpecProfileSimulationAnalysis(
        SpecProfileAnnualSalaryAnalyzationResDto analysis,
        String preparationPeriod,
        String estimatedCost,
        String decisionCriteria
) {}
