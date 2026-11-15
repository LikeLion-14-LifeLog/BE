package com.lifelog.domain.specprofile.dto;

import lombok.Builder;

import java.util.List;

/** GPT가 생성하는 값만 포함한다. 관측 통계와 적용 스펙은 서버가 구성한다. */
@Builder
public record AnnualSalaryAnalyzationAiResDto(
        String summary,
        List<SalaryPrediction> salaryResults,
        String comparisonGroupDescription,
        String recommendation
) {
    @Builder
    public record SalaryPrediction(Integer annual, Long annualIncomeMin,
                                   Long annualIncomeExpected, Long annualIncomeMax) {}
}
