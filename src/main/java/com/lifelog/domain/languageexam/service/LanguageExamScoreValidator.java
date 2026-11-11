package com.lifelog.domain.languageexam.service;

import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.specprofile.dto.LanguageExamScoreSaveReqDto;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.global.exception.CustomException;
import org.springframework.stereotype.Component;

@Component
public class LanguageExamScoreValidator {
    /** 점수유형별 숫자 및 등급의 필수 여부만 검사한다. */
    public void validate(LanguageExamScoreSaveReqDto score, ScoreType type) {
        boolean numeric = score.scoreNumeric() != null;
        boolean grade = score.scoreGrade() != null && !score.scoreGrade().isBlank();
        boolean valid = switch (type) {
            case NUMERIC -> numeric && score.scoreGrade() == null;
            case GRADE -> !numeric && grade;
            case NUMERIC_AND_GRADE -> numeric && grade;
            case NONE -> !numeric && score.scoreGrade() == null;
        };
        if (!valid) {
            throw new CustomException(SpecProfileErrorCode.INVALID_INPUT);
        }
    }
}

