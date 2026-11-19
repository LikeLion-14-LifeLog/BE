package com.lifelog.domain.specprofile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

/** 여러 시나리오의 예측과 대안의 종합 평가를 한 번에 반환하는 내부 AI 응답. */
@Builder
public record SpecProfileTargetAnalysisAiResDto(
        @NotNull @Size(min = 2, max = 4) List<@NotNull @Valid Prediction> analyses,
        @NotNull @Valid SpecProfileTargetEvaluationAiResDto evaluation
) {
    public static SpecProfileTargetAnalysisAiResDto create(List<Prediction> analyses,
            SpecProfileTargetEvaluationAiResDto evaluation) {
        return SpecProfileTargetAnalysisAiResDto.builder().analyses(analyses).evaluation(evaluation).build();
    }

    @Builder
    public record Prediction(
            @Min(0) @Max(3) int slotNumber,
            @NotNull SpecProfileSimulationAiResDto analysis
    ) {}
}
