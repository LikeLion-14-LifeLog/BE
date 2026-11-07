package com.lifelog.domain.languageexam.dto;

import java.util.List;

public record LanguageExamListResponse(
        List<LanguageExamResponse> languageExams,
        int totalCount
) {
    public static LanguageExamListResponse from(List<LanguageExamResponse> languageExams) {
        return new LanguageExamListResponse(languageExams, languageExams.size());
    }
}
