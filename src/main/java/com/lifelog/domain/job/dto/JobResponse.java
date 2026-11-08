package com.lifelog.domain.job.dto;

import com.lifelog.domain.job.entity.Job;

public record JobResponse(
        Long jobId,
        JobCategoryResponse mainCategory,
        JobCategoryResponse middleCategory,
        JobCategoryResponse subCategory,
        String code,
        String name
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                new JobCategoryResponse(job.getMainCategoryCode(), job.getMainCategoryName()),
                new JobCategoryResponse(job.getMiddleCategoryCode(), job.getMiddleCategoryName()),
                new JobCategoryResponse(job.getSubCategoryCode(), job.getSubCategoryName()),
                job.getCode(),
                job.getName()
        );
    }
}
