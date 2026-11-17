package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileSimulationResDto(
        Long baseSpecProfileId,
        CompanyGroupScale companyGroupScale,
        ScenarioResult base,
        ScenarioResult simulated,
        @Schema(description = "추가 목표 조건 달성에 필요한 참고 준비 기간. 추가 조건이 없으면 null", example = "약 2~3개월", nullable = true)
        String preparationPeriod,
        @Schema(description = "추가 목표 조건 달성에 필요한 참고 비용과 산정 가정. 추가 조건이 없으면 null", example = "약 35만 원 (응시료·교재비 기준)", nullable = true)
        String estimatedCost,
        @Schema(description = "희망 직무에서 목표 조건의 활용도와 선택 판단 기준. 추가 조건이 없으면 null", example = "기본 직무 요건 보완에 적합", nullable = true)
        String decisionCriteria
) {
    /** 현재·목표 분석을 시뮬레이션 응답으로 조합한다. */
    public static SpecProfileSimulationResDto create(Long baseSpecProfileId, CompanyGroupScale companyGroupScale,
            ScenarioResult base, ScenarioResult simulated, String preparationPeriod,
            String estimatedCost, String decisionCriteria) {
        return SpecProfileSimulationResDto.builder().baseSpecProfileId(baseSpecProfileId)
                .companyGroupScale(companyGroupScale).base(base).simulated(simulated)
                .preparationPeriod(preparationPeriod).estimatedCost(estimatedCost).decisionCriteria(decisionCriteria).build();
    }

    @Builder
    public record ScenarioResult(List<SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult> salaryResults) {
        /** 기존 분석 결과의 연차별 예측을 시나리오 응답으로 감싼다. */
        public static ScenarioResult create(List<SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult> salaryResults) {
            return ScenarioResult.builder().salaryResults(List.copyOf(salaryResults)).build();
        }
    }
}
