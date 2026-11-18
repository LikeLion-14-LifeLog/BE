package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/** 선택된 TARGET에 저장한 추가 목표만 표시하는 조회 응답. */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileTargetResDto(
        @Schema(description = "선택된 TARGET 프로필 PK", example = "11") Long targetSpecProfileId,
        @Schema(description = "기준 CURRENT 프로필 PK", example = "10") Long baseSpecProfileId,
        @Schema(description = "저장한 기업 규모. 미지정이면 null", example = "LARGE", nullable = true)
        CompanyGroupScale companyGroupScale,
        @Schema(description = "CURRENT 복제 항목을 제외한 사용자 추가 목표") Changes changes
) {
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Changes(
            List<AcademicAbilityResDto> academicAbilities,
            List<CertificateResDto> certificates,
            List<LanguageExamScoreResDto> languageExamScores,
            List<ExperienceResDto> experiences
    ) {}
}
