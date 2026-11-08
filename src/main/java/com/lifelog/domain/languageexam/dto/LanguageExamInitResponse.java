package com.lifelog.domain.languageexam.dto;

public record LanguageExamInitResponse(
        int totalCount,
        int createdCount,
        int updatedCount,
        int unchangedCount
) {
}
