package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공고 저장 상태")
public record JobPostingBookmarkResponse(
        @Schema(description = "채용공고 ID", example = "101")
        long jobPostingId,
        @Schema(description = "저장 여부 (저장 후 true, 저장 취소 후 false)", example = "true")
        boolean bookmarked,
        @Schema(description = "반영 후 저장 수", example = "313")
        int bookmarkCount
) {
}
