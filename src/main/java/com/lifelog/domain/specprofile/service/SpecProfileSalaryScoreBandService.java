package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.AcademicCondition;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.LanguageCondition;
import com.lifelog.domain.specprofile.dto.SpecProfileScoreBand;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class SpecProfileSalaryScoreBandService {
    private static final Map<String, BigDecimal> ENGLISH_STEPS = Map.of(
            "TOEIC", new BigDecimal("50"), "TOEFL", new BigDecimal("10"),
            "TEPS", new BigDecimal("50"), "IELTS", BigDecimal.ONE,
            "TOEIC SPEAKING", new BigDecimal("20"), "TOEIC WRITING", new BigDecimal("20"));

    /**
     * DB 접근 없이 학력·어학 항목별 원 값과 연결된 점수 구간을 계산한다.
     * @param data 구간 계산 전 조건
     * @return 원 조건값과 점수 구간을 함께 가진 분류 조건
     */
    public SpecProfileSalaryClassificationData classify(SpecProfileSalaryClassificationData data) {
        log.info("[SpecProfileSalaryScoreBandService] 구간 분류 | classify() - START");
        List<AcademicCondition> academics = data.academicAbilities().stream().map(academic -> new AcademicCondition(
                academic.type(), academic.location(), academic.majorAffiliation(), academic.majorName(),
                academic.universityGrade(), academic.universityStandardGrade(),
                gradeBand(academic.universityGrade(), academic.universityStandardGrade()))).toList();
        List<LanguageCondition> languages = data.languageExamScores().stream().map(score -> new LanguageCondition(
                score.languageExamId(), score.languageExamName(), score.scoreNumeric(), score.scoreGrade(),
                englishBand(score.languageExamName(), score.scoreNumeric()),
                score.scoreGrade() == null || score.scoreGrade().isBlank() ? null : score.scoreGrade().strip().toUpperCase(Locale.ROOT))).toList();
        SpecProfileSalaryClassificationData result = new SpecProfileSalaryClassificationData(
                data.jobSubCategoryCode(), data.jobSubCategoryName(), academics, languages, data.certificateCount());
        log.info("[SpecProfileSalaryScoreBandService] 구간 분류 | classify() - END");
        return result;
    }

    /**
     * 만점별 학점 구간을 계산한다. 4.3점제도 0.5점 간격으로 마지막 구간을 절단한다.
     * @param grade 학점
     * @param scale 만점
     * @return 학점 구간 또는 두 입력 모두 미입력일 때 null
     */
    public SpecProfileScoreBand gradeBand(BigDecimal grade, BigDecimal scale) {
        log.debug("[SpecProfileSalaryScoreBandService] 학점 구간 | gradeBand() - START");
        if (grade == null && scale == null) {
            log.debug("[SpecProfileSalaryScoreBandService] 학점 구간 | gradeBand() - END | missing: true");
            return null;
        }
        if (grade == null || scale == null || grade.signum() < 0 || grade.compareTo(scale) > 0
                || List.of("4.0", "4.3", "4.5", "100").stream().noneMatch(value -> new BigDecimal(value).compareTo(scale) == 0)) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_CLASSIFICATION_SCORE);
        }
        BigDecimal step = scale.compareTo(new BigDecimal("100")) == 0 ? BigDecimal.TEN : new BigDecimal("0.5");
        BigDecimal lower = grade.divide(step, 0, RoundingMode.FLOOR).multiply(step);
        if (grade.compareTo(scale) == 0 && grade.remainder(step).signum() == 0) {
            lower = lower.subtract(step);
        }
        BigDecimal upper = lower.add(step).min(scale);
        SpecProfileScoreBand result = new SpecProfileScoreBand(lower, upper, upper.compareTo(scale) == 0);
        log.debug("[SpecProfileSalaryScoreBandService] 학점 구간 | gradeBand() - END");
        return result;
    }

    /**
     * 시험별 간격으로 숫자 성적을 분류한다. 시험 버전 간 점수 환산은 수행하지 않는다.
     * @param examName 시험명
     * @param score 숫자 성적
     * @return 숫자 구간 또는 숫자 미입력·체계 미정 시험일 때 null
     */
    public SpecProfileScoreBand englishBand(String examName, BigDecimal score) {
        log.debug("[SpecProfileSalaryScoreBandService] 영어 구간 | englishBand() - START");
        if (score != null && score.signum() < 0) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_CLASSIFICATION_SCORE);
        }
        BigDecimal step = examName == null ? null : ENGLISH_STEPS.get(examName.strip().toUpperCase(Locale.ROOT));
        SpecProfileScoreBand result = null;
        if (score != null && step != null) {
            BigDecimal lower = score.divide(step, 0, RoundingMode.FLOOR).multiply(step);
            result = new SpecProfileScoreBand(lower, lower.add(step), false);
        } else if (score != null) {
            log.warn("[SpecProfileSalaryScoreBandService] 숫자 구간 정책이 없는 시험 | examName: {}", examName);
        }
        log.debug("[SpecProfileSalaryScoreBandService] 영어 구간 | englishBand() - END");
        return result;
    }
}
