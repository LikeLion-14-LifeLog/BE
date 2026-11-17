package com.lifelog.domain.specprofile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
public record SpecProfileTargetRecommendationAiResDto(
        @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid Option> options
) {
    @Builder
    public record Option(
            @NotBlank @Size(max = 255) String title,
            @NotNull @Valid SpecProfileTargetChanges changes,
            @NotBlank String improvementReason
    ) {}
}
