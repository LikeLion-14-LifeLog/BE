package com.lifelog.domain.languageexam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "어학시험 목록 조회 결과")
public record LanguageExamListResponse(
        @Schema(description = "어학시험 목록")
        List<LanguageExamResponse> languageExams,
        @Schema(description = "전체 건수", example = "9")
        int totalCount
) {
    public static LanguageExamListResponse from(List<LanguageExamResponse> languageExams) {
        return new LanguageExamListResponse(languageExams, languageExams.size());
    }
}
