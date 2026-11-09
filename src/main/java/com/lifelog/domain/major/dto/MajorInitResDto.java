package com.lifelog.domain.major.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "전공 기준 데이터 등록/갱신 결과")
public record MajorInitResDto(
        @Schema(description = "헤더와 빈 줄을 제외한 원본 데이터 행 수", example = "37656") int sourceRowCount,
        @Schema(description = "동일 코드·동일 내용으로 제외한 중복 행 수", example = "2") int duplicateRowCount,
        @Schema(description = "중복 제거 후 처리 대상 수", example = "37654") int totalCount,
        @Schema(description = "신규 등록 수", example = "37654") int createdCount,
        @Schema(description = "실제 값이 변경된 수", example = "0") int updatedCount,
        @Schema(description = "변경하지 않은 수", example = "0") int unchangedCount
) {
    public static MajorInitResDto create(int sourceRowCount, int duplicateRowCount, int totalCount,
                                         int createdCount, int updatedCount, int unchangedCount) {
        return MajorInitResDto.builder()
                .sourceRowCount(sourceRowCount).duplicateRowCount(duplicateRowCount).totalCount(totalCount)
                .createdCount(createdCount).updatedCount(updatedCount).unchangedCount(unchangedCount)
                .build();
    }
}
