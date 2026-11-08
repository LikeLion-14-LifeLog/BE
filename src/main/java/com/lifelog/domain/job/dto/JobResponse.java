package com.lifelog.domain.job.dto;

import com.lifelog.domain.job.entity.Job;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "직무")
public record JobResponse(
        @Schema(description = "직무 ID", example = "1")
        Long jobId,
        @Schema(description = "대분류 (미분류 직무는 code가 null)")
        JobCategoryResponse mainCategory,
        @Schema(description = "중분류 (미분류 직무는 null)", nullable = true)
        JobCategoryResponse middleCategory,
        @Schema(description = "소분류 (미분류 직무는 null)", nullable = true)
        JobCategoryResponse subCategory,
        @Schema(description = "세분류 코드 (4자리, 앞자리 0 유지)", example = "0111")
        String code,
        @Schema(description = "직무명", example = "의회의원·고위공무원 및 공공단체임원")
        String name
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                new JobCategoryResponse(job.getMainCategoryCode(), job.getMainCategoryName()),
                categoryOrNull(job.getMiddleCategoryCode(), job.getMiddleCategoryName()),
                categoryOrNull(job.getSubCategoryCode(), job.getSubCategoryName()),
                job.getCode(),
                job.getName()
        );
    }

    private static JobCategoryResponse categoryOrNull(String code, String name) {
        return code == null ? null : new JobCategoryResponse(code, name);
    }
}
