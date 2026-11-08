package com.lifelog.domain.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "직무 기준 데이터 등록/갱신 결과")
public record JobInitResponse(
        @Schema(description = "CSV 전체 건수", example = "451")
        int totalCount,
        @Schema(description = "새로 생성된 건수", example = "451")
        int createdCount,
        @Schema(description = "값이 달라 수정된 건수", example = "0")
        int updatedCount,
        @Schema(description = "변경 없는 건수", example = "0")
        int unchangedCount
) {
}
