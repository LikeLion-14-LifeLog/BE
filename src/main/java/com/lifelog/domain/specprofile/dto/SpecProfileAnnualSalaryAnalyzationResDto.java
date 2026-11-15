package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/** 분석 결과 계약. 연차별 예측 범위와 실제 표본의 분위수를 분리한다. */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileAnnualSalaryAnalyzationResDto(
        String summary,
        List<SalaryResult> salaryResults,
        ComparisonGroup comparisonGroup,
        AppliedSpecs appliedSpecs,
        KeepSalaryMatchedSampleStatistics statistics,
        String recommendation,
        String salaryNotice
) {
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SalaryResult(int annual, Long annualIncomeMin, Long annualIncomeExpected,
                               Long annualIncomeMax, BigDecimal topPercent) {}

    @Builder
    public record ComparisonGroup(String jobSubCategoryCode, String jobSubCategoryName,
                                  int sampleCount, List<Integer> observationYears, String description) {}

    @Builder
    public record AppliedSpecs(Integer birthYear, ResidentialArea residenceRegion, Long jobId, String jobName,
                               List<AcademicAbilityResDto> academicAbilities,
                               List<CertificateResDto> certificates,
                               List<LanguageExamScoreResDto> languageExamScores,
                               List<ExperienceResDto> experiences) {}
}
