package com.lifelog.domain.languageexam.service;

import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.specprofile.dto.LanguageExamScoreSaveReqDto;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.global.exception.CustomException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class LanguageExamScoreValidator {
    /** 점수유형별 숫자 및 등급의 필수 여부만 검사한다. */
    public void validate(LanguageExamScoreSaveReqDto score, ScoreType type) {
        validate(type, score.scoreNumeric(), score.scoreGrade());
    }

    /** 저장과 시뮬레이션에서 공통으로 시험별 성적 입력 조합을 검증한다. */
    public void validate(ScoreType type, BigDecimal scoreNumeric, String scoreGrade) {
        boolean numeric = scoreNumeric != null;
        boolean grade = scoreGrade != null && !scoreGrade.isBlank();
        boolean valid = switch (type) {
            case NUMERIC -> numeric && scoreGrade == null;
            case GRADE -> !numeric && grade;
            case NUMERIC_AND_GRADE -> numeric && grade;
            case NONE -> !numeric && scoreGrade == null;
        };
        if (!valid) {
            throw new CustomException(SpecProfileErrorCode.INVALID_INPUT);
        }
    }
}

