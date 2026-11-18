package com.lifelog.domain.specprofile.dto;

import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "사용자 TARGET 저장 및 저장하지 않는 AI 대안 비교 요청. 예시의 baseSpecProfileId를 본인 CURRENT ID로 변경하세요.", example = """
        {
          "baseSpecProfileId": 10,
          "companyGroupScale": "LARGE",
          "changes": {
            "academicAbilities": [],
            "certificates": [],
            "languageExamScores": [],
            "experiences": [
              {
                "type": "INTERNSHIP",
                "title": "백엔드 개발 인턴",
                "startedYearMonth": "2027-01",
                "endedYearMonth": "2027-06",
                "description": "Spring Boot 기반 API 개발 및 서비스 운영 경험",
                "awarded": false,
                "organizerName": null,
                "awardGrade": null
              }
            ]
          }
        }
        """)
public record SpecProfileTargetSaveReqDto(
        @Schema(description = "GET /api/spec-profile 응답의 data.specProfileId. 예시 10을 본인 CURRENT ID로 변경", example = "10")
        @NotNull @Positive Long baseSpecProfileId,
        @Schema(description = "모든 선택지에 적용할 기업 규모. 생략 시 미지정", example = "LARGE", nullable = true)
        CompanyGroupScale companyGroupScale,
        @Schema(description = "현재 스펙에 추가할 목표. 기본 예시는 별도 마스터 ID가 필요 없는 인턴 경험이며 빈 목록은 추가하지 않음")
        @Valid SpecProfileTargetChanges changes
) {
    public SpecProfileTargetSaveReqDto {
        changes = changes == null ? SpecProfileTargetChanges.empty() : changes;
    }
}
