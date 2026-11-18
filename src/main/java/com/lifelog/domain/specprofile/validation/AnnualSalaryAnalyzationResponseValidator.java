package com.lifelog.domain.specprofile.validation;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto.SalaryPrediction;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAiResDto;
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

    /**
     * 시뮬레이션 준비 정보가 카드용 한 줄 형식과 해요체를 준수하는지 검증한다.
     * @param response 시뮬레이션 AI 응답
     */
    public void validateSimulationDetails(SpecProfileSimulationAiResDto response) {
        log.info("[AnnualSalaryAnalyzationResponseValidator] 목표 준비 정보 검증 | validateSimulationDetails() - START");
        if (response == null || !isSimulationDisplayReady(response.preparationPeriod(), response.estimatedCost(),
                response.decisionCriteria())) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_AI_RESPONSE);
        }
        log.info("[AnnualSalaryAnalyzationResponseValidator] 목표 준비 정보 검증 | validateSimulationDetails() - END");
    }

    /** 새 AI 응답과 기존 캐시에 동일한 카드 표시 규칙을 적용한다. */
    public boolean isSimulationDisplayReady(String preparationPeriod, String estimatedCost, String decisionCriteria) {
        return isCompactLabel(preparationPeriod, SpecProfileSimulationAiResDto.PREPARATION_PERIOD_MAX_LENGTH)
                && isCompactLabel(estimatedCost, SpecProfileSimulationAiResDto.ESTIMATED_COST_MAX_LENGTH)
                && isCompactLabel(decisionCriteria, SpecProfileSimulationAiResDto.DECISION_CRITERIA_MAX_LENGTH)
                && decisionCriteria.matches(SpecProfileSimulationAiResDto.POLITE_SENTENCE_PATTERN);
    }

    /** 여러 줄 설명과 과도하게 긴 준비 정보가 비교 카드에 노출되지 않도록 검사한다. */
    private boolean isCompactLabel(String text, int maxLength) {
        return !isBlank(text) && text.length() <= maxLength
                && text.matches(SpecProfileSimulationAiResDto.SINGLE_LINE_PATTERN);
    }

    /** 필수 설명이 누락됐는지 검사한다. */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
