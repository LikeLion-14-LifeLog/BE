package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileSimulationResDto(
        Long baseSpecProfileId,
        CompanyGroupScale companyGroupScale,
        ScenarioResult base,
        ScenarioResult simulated
) {
    /** 현재·목표 분석을 시뮬레이션 응답으로 조합한다. */
    public static SpecProfileSimulationResDto create(Long baseSpecProfileId, CompanyGroupScale companyGroupScale,
                                                     ScenarioResult base, ScenarioResult simulated) {
        return SpecProfileSimulationResDto.builder().baseSpecProfileId(baseSpecProfileId)
                .companyGroupScale(companyGroupScale).base(base).simulated(simulated).build();
    }

    @Builder
    public record ScenarioResult(List<SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult> salaryResults) {
        /** 기존 분석 결과의 연차별 예측을 시나리오 응답으로 감싼다. */
        public static ScenarioResult create(List<SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult> salaryResults) {
            return ScenarioResult.builder().salaryResults(List.copyOf(salaryResults)).build();
        }
    }
}
