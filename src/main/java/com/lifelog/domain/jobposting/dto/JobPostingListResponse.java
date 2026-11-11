package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "맞춤 공고 목록")
public record JobPostingListResponse(
        @Schema(description = "맞춤 조건(희망·목표 직무, 학력) 적용 여부. 희망·목표 직무가 없으면 false이고 전체 공고를 보여줌", example = "true")
        boolean personalized,
        @Schema(description = "탭별 건수")
        JobPostingTabCounts tabCounts,
        @Schema(description = "현재 페이지의 공고 카드")
        List<JobPostingCardResponse> jobPostings
) {
}
