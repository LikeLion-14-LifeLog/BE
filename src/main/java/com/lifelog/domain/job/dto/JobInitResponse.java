package com.lifelog.domain.job.dto;

public record JobInitResponse(
        int totalCount,
        int createdCount,
        int updatedCount,
        int unchangedCount
) {
}
