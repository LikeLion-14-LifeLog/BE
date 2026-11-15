package com.lifelog.domain.jobposting.dto;

import com.lifelog.domain.job.entity.Job;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "맞춤 공고 기준이 되는 희망 직무")
public record JobPostingDesiredJobResponse(
        @Schema(description = "직무 코드 4자리 (한국고용직업분류 2025 세분류)", example = "1332")
        String code,
        @Schema(description = "직무 이름", example = "응용 소프트웨어 개발자")
        String name
) {

    public static JobPostingDesiredJobResponse from(Job job) {
        return new JobPostingDesiredJobResponse(job.getCode(), job.getName());
    }
}
