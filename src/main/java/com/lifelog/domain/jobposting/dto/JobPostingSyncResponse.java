package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용공고 수집 결과")
public record JobPostingSyncResponse(
        @Schema(description = "원본에서 받은 공고 수 (중복 제거 후)", example = "39752")
        int totalCount,
        @Schema(description = "새로 저장한 공고 수", example = "120")
        int createdCount,
        @Schema(description = "내용이 달라 수정한 공고 수", example = "35")
        int updatedCount,
        @Schema(description = "변경 없는 공고 수", example = "39597")
        int unchangedCount,
        @Schema(description = "모집 중에서 마감으로 바뀐 공고 수 (원본에서 사라졌거나 마감일 경과)", example = "80")
        int closedCount
) {
}
