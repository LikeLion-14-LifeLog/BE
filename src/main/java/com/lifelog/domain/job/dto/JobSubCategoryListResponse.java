package com.lifelog.domain.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "직무 소분류 검색 결과")
public record JobSubCategoryListResponse(
        @Schema(description = "소분류 목록 (code 오름차순)")
        List<JobSubCategoryResponse> subCategories
) {
}
