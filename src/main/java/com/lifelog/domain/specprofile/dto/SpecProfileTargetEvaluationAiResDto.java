package com.lifelog.domain.specprofile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
public record SpecProfileTargetEvaluationAiResDto(
        @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid Evaluation> evaluations
) {
    @Builder
    public record Evaluation(
            @Min(2) @Max(3) int slotNumber,
            @NotNull Boolean betterOverall,
            @NotBlank String reason
    ) {}
}
