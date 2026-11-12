package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공고 직무 필터용 소분류 검색 결과")
public record JobPostingJobCategoryListResponse(
        @Schema(description = "소분류 목록 (code 오름차순)")
        List<JobPostingJobCategoryResponse> jobCategories
) {
}
