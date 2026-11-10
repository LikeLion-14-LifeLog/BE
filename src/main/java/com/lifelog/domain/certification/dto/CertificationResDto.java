package com.lifelog.domain.certification.dto;

import com.lifelog.domain.certification.enums.CertificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "자격증 조회 결과")
public record CertificationResDto(
        Long certificationId,
        String name,
        @Schema(nullable = true) String code,
        CertificationType type,
        String mainCategoryName,
        @Schema(nullable = true) String authorizedInstitutionName
) {
    public static CertificationResDto create(Long certificationId, String name, String code,
                                             CertificationType type, String mainCategoryName,
                                             String authorizedInstitutionName) {
        return CertificationResDto.builder().certificationId(certificationId).name(name).code(code)
                .type(type).mainCategoryName(mainCategoryName)
                .authorizedInstitutionName(authorizedInstitutionName).build();
    }
}
