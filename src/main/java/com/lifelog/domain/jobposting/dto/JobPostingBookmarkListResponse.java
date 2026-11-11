package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "저장한 공고 목록")
public record JobPostingBookmarkListResponse(
        @Schema(description = "현재 페이지의 공고 카드 (최근 저장 순)")
        List<JobPostingCardResponse> jobPostings
) {
}
