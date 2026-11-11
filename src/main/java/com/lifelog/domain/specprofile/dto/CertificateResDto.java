package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.time.YearMonth;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CertificateResDto(
    Long specProfileCertificateId,
    Long certificateId,
    String certificateName,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
    @Schema(type = "string", example = "2025-02", nullable = true)
    YearMonth acquiredYearMonth
) {}
