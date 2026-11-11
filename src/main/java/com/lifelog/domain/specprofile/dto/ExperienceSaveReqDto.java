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

public record ExperienceSaveReqDto(
    @Schema(description = "기존 경험 PK. 신규 등록 시 생략 또는 null", nullable = true, example = "null")
    @Positive Long experienceId,
    @Schema(description = "활동 유형", example = "INTERNSHIP")
    @NotNull ExperienceType type,
    @Schema(description = "활동명", example = "핀테크 플랫폼 백엔드 개발 인턴")
    @NotBlank @Size(max = 255) String title,
    @Schema(description = "활동 시작 연월. 미래 월 불가", type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2025-07")
    @NotNull YearMonth startedYearMonth,
    @Schema(description = "활동 종료 연월. 진행 중이면 null", type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", nullable = true, example = "2025-10")
    YearMonth endedYearMonth,
    @Schema(description = "활동 중 역할 및 성과", example = "결제 승인 API 개발 및 Redis 캐싱 계층 설계")
    @Size(max = 300) String description,
    @Schema(description = "수상 여부", example = "false")
    @NotNull Boolean awarded,
    @Schema(description = "수상 시 필수인 주관 기관명. 미수상 시 null", nullable = true)
    @Size(max = 200) String organizerName,
    @Schema(description = "수상 시 필수인 수상 등급. 미수상 시 null", nullable = true)
    @Size(max = 50) String awardGrade
) {
}

