package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용공고 수집 결과")
public record JobPostingSyncResponse(
        @Schema(description = "저장 대상 공고 수 (중복·변환 실패 제외)", example = "39752")
        int totalCount,
        @Schema(description = "새로 저장한 공고 수", example = "120")
        int createdCount,
        @Schema(description = "내용이 달라 수정한 공고 수", example = "35")
        int updatedCount,
        @Schema(description = "변경 없는 공고 수", example = "39597")
        int unchangedCount,
        @Schema(description = "모집 중에서 마감으로 바뀐 공고 수 (원본에서 사라졌거나 마감일 경과)", example = "80")
        int closedCount,
        @Schema(description = "읽기·변환에 실패해 건너뛴 공고 수 (기존 데이터 유지, 마감하지 않음)", example = "0")
        int skippedCount,
        @Schema(description = "원본을 전체 건수만큼 받아 마감 처리까지 했는지. false면 받은 공고만 저장하고 마감 처리는 건너뜀", example = "true")
        boolean complete
) {

    public static JobPostingSyncResponse empty() {
        return new JobPostingSyncResponse(0, 0, 0, 0, 0, 0, false);
    }
}
