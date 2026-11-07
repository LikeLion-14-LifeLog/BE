package com.lifelog.domain.languageexam.dto;

public record LanguageExamImportResponse(
        int totalCount,
        int createdCount,
        int updatedCount,
        int unchangedCount
) {
}
