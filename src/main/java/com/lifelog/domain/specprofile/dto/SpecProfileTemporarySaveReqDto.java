package com.lifelog.domain.specprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.major.enums.MajorMainField;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Schema(description = "회원 스펙 임시 저장 요청", example = """
        {
          "birthYear": 1999,
          "residenceRegion": "DAEGU",
          "jobId": 1,
          "academicAbilities": [
            {
              "academicAbilityId": null,
              "type": "UNIVERSITY",
              "status": "GRADUATED",
              "location": "SEOUL",
              "universityGrade": 3.75,
              "universityStandardGrade": 4.5,
              "graduatedYearMonth": "2025-02",
              "majorAffiliation": "HUMANITIES",
              "majorId": 10
            }
          ],
          "certificates": [
            {
              "specProfileCertificateId": null,
              "certificateId": 1,
              "acquiredYearMonth": "2024-11"
            }
          ],
          "languageExamScores": [
            {
              "languageExamScoreId": null,
              "languageExamId": 1,
              "scoreNumeric": 865,
              "scoreGrade": null,
              "acquiredYearMonth": "2024-08",
              "expiresYearMonth": "2026-08"
            }
          ],
          "experiences": [
            {
              "experienceId": null,
              "type": "INTERNSHIP",
              "title": "핀테크 플랫폼 백엔드 개발 인턴",
              "startedYearMonth": "2025-07",
              "endedYearMonth": "2025-10",
              "description": "결제 승인 API 개발 및 Redis 캐싱 계층 설계",
              "awarded": false,
              "organizerName": null,
              "awardGrade": null
            }
          ]
        }
        """)
public record SpecProfileTemporarySaveReqDto(
    @Schema(description = "출생년도. 미래 연도 불가", example = "1999")
    @NotNull(message = "기본 정보는 필수 입력값입니다") @Min(1) Integer birthYear,

    @Schema(description = "거주지역", example = "DAEGU")
    @NotNull(message = "기본 정보는 필수 입력값입니다") ResidentialArea residenceRegion,

    @Schema(description = "등록된 희망 직무 PK", example = "1")
    @NotNull(message = "기본 정보는 필수 입력값입니다") @Positive Long jobId,

    @Schema(description = "학력 목록. null 또는 빈 배열이면 기존 학력 삭제", nullable = true)
    List<@NotNull @Valid AcademicAbilitySaveReqDto> academicAbilities,

    @Schema(description = "보유 자격증 목록. 없으면 빈 배열")
    @NotNull List<@NotNull @Valid CertificateSaveReqDto> certificates,

    @Schema(description = "어학 성적 목록. 없으면 빈 배열")
    @NotNull List<@NotNull @Valid LanguageExamScoreSaveReqDto> languageExamScores,

    @Schema(description = "활동 경험 목록. 없으면 빈 배열")
    @NotNull List<@NotNull @Valid ExperienceSaveReqDto> experiences
) {
    public SpecProfileTemporarySaveReqDto {
        academicAbilities = academicAbilities == null ? List.of() : academicAbilities;
    }
}

