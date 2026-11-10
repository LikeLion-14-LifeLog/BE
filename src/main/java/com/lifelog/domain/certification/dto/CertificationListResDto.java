package com.lifelog.domain.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "현재 페이지의 자격증 목록")
public record CertificationListResDto(List<CertificationResDto> certifications) {
    public static CertificationListResDto create(List<CertificationResDto> certifications) {
        return CertificationListResDto.builder().certifications(certifications).build();
    }
}
