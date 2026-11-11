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

public record CertificateSaveReqDto(
    @Schema(description = "기존 보유 자격증 PK. 신규 등록 시 생략 또는 null", nullable = true, example = "null")
    @Positive Long specProfileCertificateId,
    @Schema(description = "등록된 자격증 PK", example = "1")
    @NotNull @Positive Long certificateId,
    @Schema(description = "자격증 취득 연월", type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2024-11")
    @NotNull YearMonth acquiredYearMonth
) {
}

