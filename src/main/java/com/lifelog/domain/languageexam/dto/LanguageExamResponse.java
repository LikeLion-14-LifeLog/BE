package com.lifelog.domain.languageexam.dto;

import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;

public record LanguageExamResponse(
        Long languageExamId,
        String name,
        LanguageType languageType,
        ScoreType scoreType
) {
    public static LanguageExamResponse from(LanguageExam languageExam) {
        return new LanguageExamResponse(
                languageExam.getId(),
                languageExam.getName(),
                languageExam.getLanguageType(),
                languageExam.getScoreType()
        );
    }
}
