package com.lifelog.domain.languageexam.dto;

import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "어학시험")
public record LanguageExamResponse(
        @Schema(description = "어학시험 ID", example = "1")
        Long languageExamId,
        @Schema(description = "시험명", example = "TOEIC")
        String name,
        @Schema(description = "언어 종류", example = "ENGLISH")
        LanguageType languageType,
        @Schema(description = "점수 유형", example = "NUMERIC")
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
