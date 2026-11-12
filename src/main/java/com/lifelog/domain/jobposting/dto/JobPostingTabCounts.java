package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "맞춤 공고 탭별 건수")
public record JobPostingTabCounts(
        @Schema(description = "맞춤 조건까지 적용한 모집 중 공고 수 (직무 그룹·지역 필터 적용 전, 마감일 지난 공고 제외)", example = "24")
        long recommended,
        @Schema(description = "내가 저장한 공고 수", example = "2")
        long bookmarked
) {
}
