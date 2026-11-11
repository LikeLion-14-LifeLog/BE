package com.lifelog.domain.jobposting.dto;

import com.lifelog.domain.job.entity.Job;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공고 직무 필터용 소분류 (검색형 선택 UI)")
public record JobPostingJobCategoryResponse(
        @Schema(description = "소분류 코드 (공고 목록 jobCategory 값)", example = "133")
        String code,
        @Schema(description = "소분류명", example = "소프트웨어 개발자")
        String name,
        @Schema(description = "대분류")
        CategoryResponse mainCategory,
        @Schema(description = "중분류")
        CategoryResponse middleCategory,
        @Schema(description = "이 소분류에 속한 세분류 직무 (검색어가 세분류명에 걸린 경우 확인용)")
        List<CategoryResponse> jobs
) {

    @Schema(description = "분류 코드와 이름")
    public record CategoryResponse(
            @Schema(description = "코드", example = "13")
            String code,
            @Schema(description = "이름", example = "정보통신 연구개발직 및 공학기술직")
            String name
    ) {
    }

    // jobs는 같은 소분류의 세분류 직무 (code 오름차순)
    public static JobPostingJobCategoryResponse from(List<Job> jobs) {
        Job first = jobs.getFirst();
        return new JobPostingJobCategoryResponse(
                first.getSubCategoryCode(),
                first.getSubCategoryName(),
                new CategoryResponse(first.getMainCategoryCode(), first.getMainCategoryName()),
                new CategoryResponse(first.getMiddleCategoryCode(), first.getMiddleCategoryName()),
                jobs.stream().map(job -> new CategoryResponse(job.getCode(), job.getName())).toList()
        );
    }
}
