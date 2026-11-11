package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "맞춤 공고 목록")
public record JobPostingListResponse(
        @Schema(description = "탭별 건수")
        JobPostingTabCounts tabCounts,
        @Schema(description = "현재 페이지의 공고 카드")
        List<JobPostingCardResponse> jobPostings
) {
}
