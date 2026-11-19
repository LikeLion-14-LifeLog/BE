package com.lifelog.domain.specprofile.policy;

import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationContext;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import com.lifelog.domain.specprofile.validation.AnnualSalaryAnalyzationResponseValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SpecProfileTargetCachePolicy {
    private final SpecProfileTargetMapper mapper;
    private final AnnualSalaryAnalyzationResponseValidator responseValidator;

    /** 동일 CURRENT·추가 스펙·기업 규모·저장 기준 분석의 완전한 최신 결과만 재사용한다. */
    public boolean canReuse(SpecProfileSimulationContext prepared, SpecProfileSimulationContext cached, CompanyGroupScale scale,
            SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis) {
        if (cached == null || cached.base() == null || cached.simulated() == null || prepared.simulated() == null
                || cached.response() == null || !Objects.equals(scale, cached.companyGroupScale())) return false;
        return mapper.comparisonKey(prepared.base()).equals(mapper.comparisonKey(cached.base()))
                && mapper.comparisonKey(prepared.simulated()).equals(mapper.comparisonKey(cached.simulated()))
                && validReport(cached.baseAnalysis()) && validReport(cached.simulatedAnalysis())
                && mapper.comparisonKey(baseAnalysis).equals(mapper.comparisonKey(cached.baseAnalysis()))
                && responseValidator.isSimulationDisplayReady(cached.response().preparationPeriod(),
                    cached.response().estimatedCost(), cached.response().decisionCriteria());
    }

    /** 필수 연차·예측 범위·비교 통계가 누락된 캐시는 다시 계산한다. */
    private boolean validReport(SpecProfileAnnualSalaryAnalyzationResDto report) {
        if (report == null || report.salaryResults() == null || report.statistics() == null
                || report.comparisonGroup() == null || report.comparisonGroup().sampleCount() < 10
                || !notBlank(report.summary()) || !notBlank(report.salaryNotice())) return false;
        return report.salaryResults().size() == 3
                && report.salaryResults().stream().allMatch(value -> value != null && value.annualIncomeMin() != null
                    && value.annualIncomeExpected() != null && value.annualIncomeMax() != null && value.annualIncomeMin() > 0
                    && value.annualIncomeMin() <= value.annualIncomeExpected() && value.annualIncomeExpected() <= value.annualIncomeMax())
                && report.salaryResults().stream().map(SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult::annual)
                    .sorted().toList().equals(List.of(0, 3, 5));
    }

    /** 준비 정보의 빈 문자열을 유효한 추정으로 취급하지 않는다. */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
