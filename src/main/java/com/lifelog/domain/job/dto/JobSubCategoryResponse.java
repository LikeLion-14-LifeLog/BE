package com.lifelog.domain.job.dto;

import com.lifelog.domain.job.entity.Job;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "직무 소분류 (검색형 선택 UI용)")
public record JobSubCategoryResponse(
        @Schema(description = "소분류 코드 (공고 목록 jobCategory 값)", example = "133")
        String code,
        @Schema(description = "소분류명", example = "소프트웨어 개발자")
        String name,
        @Schema(description = "대분류")
        JobCategoryResponse mainCategory,
        @Schema(description = "중분류")
        JobCategoryResponse middleCategory,
        @Schema(description = "이 소분류에 속한 세분류 직무 (검색어가 세분류명에 걸린 경우 확인용)")
        List<JobCategoryResponse> jobs
) {

    // jobs는 같은 소분류의 세분류 직무 (code 오름차순)
    public static JobSubCategoryResponse from(List<Job> jobs) {
        Job first = jobs.getFirst();
        return new JobSubCategoryResponse(
                first.getSubCategoryCode(),
                first.getSubCategoryName(),
                new JobCategoryResponse(first.getMainCategoryCode(), first.getMainCategoryName()),
                new JobCategoryResponse(first.getMiddleCategoryCode(), first.getMiddleCategoryName()),
                jobs.stream().map(job -> new JobCategoryResponse(job.getCode(), job.getName())).toList()
        );
    }
}
