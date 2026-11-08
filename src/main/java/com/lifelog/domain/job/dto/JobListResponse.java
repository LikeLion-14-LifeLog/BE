package com.lifelog.domain.job.dto;

import com.lifelog.domain.job.entity.Job;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "직무 목록 조회 결과")
public record JobListResponse(
        @Schema(description = "직무 목록")
        List<JobResponse> jobs,
        @Schema(description = "현재 페이지 (1부터 시작)", example = "1")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int size,
        @Schema(description = "전체 건수", example = "451")
        long totalElements,
        @Schema(description = "전체 페이지 수 (결과가 없으면 0)", example = "23")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true")
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
