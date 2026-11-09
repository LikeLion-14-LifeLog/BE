package com.lifelog.domain.job.dto;

import com.lifelog.domain.job.entity.Job;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "현재 페이지의 직무 목록")
public record JobListResponse(
        @Schema(description = "직무 목록")
        List<JobResponse> jobs
) {
    public static JobListResponse from(List<Job> jobs) {
        return new JobListResponse(jobs.stream().map(JobResponse::from).toList());
    }
}
