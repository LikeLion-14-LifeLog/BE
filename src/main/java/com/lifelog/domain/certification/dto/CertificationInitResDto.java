package com.lifelog.domain.certification.dto;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;
@Builder
@Schema(description = "자격증 기준 데이터 등록·갱신 결과")
public record CertificationInitResDto(int totalCount, int createdCount, int updatedCount, int unchangedCount) {
    public static CertificationInitResDto create(int total, int created, int updated, int unchanged) {
        return new CertificationInitResDto(total, created, updated, unchanged);
    }
}
