package com.lifelog.domain.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "직무 분류 (대·중·소분류)")
public record JobCategoryResponse(
        @Schema(description = "분류 코드", example = "01")
        String code,
        @Schema(description = "분류명", example = "관리직(임원·부서장)")
        String name
) {
}
