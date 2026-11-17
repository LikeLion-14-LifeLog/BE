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
        @Schema(description = "추가 목표 달성에 필요한 기간만 한 줄로 표시. 추가 조건이 없으면 null", example = "약 2~3개월", maxLength = SpecProfileSimulationAiResDto.PREPARATION_PERIOD_MAX_LENGTH, nullable = true)
        String preparationPeriod,
        @Schema(description = "추가 목표의 원화 비용·범위 또는 비용 상태만 표시. 산정 가정·제외 항목 미포함. 추가 조건이 없으면 null", example = "약 0~15만 원", maxLength = SpecProfileSimulationAiResDto.ESTIMATED_COST_MAX_LENGTH, nullable = true)
        String estimatedCost,
        @Schema(description = "목표의 직무 활용도만 해요체 한 문장으로 표시. AI 판단 과정 미포함. 추가 조건이 없으면 null", example = "기본 직무 요건을 보완하는 데 적합해요.", maxLength = SpecProfileSimulationAiResDto.DECISION_CRITERIA_MAX_LENGTH, nullable = true)
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
