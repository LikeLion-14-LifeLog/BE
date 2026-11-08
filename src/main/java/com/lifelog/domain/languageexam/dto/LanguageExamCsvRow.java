package com.lifelog.domain.languageexam.dto;

import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;

public record LanguageExamCsvRow(
        Long languageExamId,
        String name,
        LanguageType languageType,
        ScoreType scoreType
) {
}
