package com.lifelog.domain.job.dto;

import com.lifelog.domain.job.entity.Job;
import org.springframework.data.domain.Page;

import java.util.List;

public record JobListResponse(
        List<JobResponse> jobs,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    // page는 요청 기준(1부터 시작)으로 응답
    public static JobListResponse of(Page<Job> result, int page, int size) {
        return new JobListResponse(
                result.getContent().stream().map(JobResponse::from).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
