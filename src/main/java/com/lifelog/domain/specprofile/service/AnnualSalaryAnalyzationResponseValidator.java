package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto.SalaryPrediction;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class AnnualSalaryAnalyzationResponseValidator {
    /**
     * JSON 스키마만으로 보장할 수 없는 연차의 중복·누락과 금액의 순서를 검증한다.
     * @param response GPT 분석 응답
     */
    public void validate(AnnualSalaryAnalyzationAiResDto response) {
        log.info("[AnnualSalaryAnalyzationResponseValidator] 예측 검증 | validate() - START");
        if (response == null || isBlank(response.summary()) || isBlank(response.comparisonGroupDescription())
                || isBlank(response.recommendation()) || response.salaryResults() == null
                || response.salaryResults().size() != 3) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_AI_RESPONSE);
        }
        Set<Integer> annuals = new HashSet<>();
        for (SalaryPrediction prediction : response.salaryResults()) {
            if (prediction == null || prediction.annual() == null || !annuals.add(prediction.annual())
                    || prediction.annualIncomeMin() == null || prediction.annualIncomeExpected() == null
                    || prediction.annualIncomeMax() == null || prediction.annualIncomeMin() <= 0
                    || prediction.annualIncomeMin() > prediction.annualIncomeExpected()
                    || prediction.annualIncomeExpected() > prediction.annualIncomeMax()) {
                throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_AI_RESPONSE);
            }
        }
        if (!annuals.equals(Set.of(0, 3, 5))) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_AI_RESPONSE);
        }
        log.info("[AnnualSalaryAnalyzationResponseValidator] 예측 검증 | validate() - END");
    }

    /** 필수 설명이 누락됐는지 검사한다. */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
