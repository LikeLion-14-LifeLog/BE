package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.LanguageCondition;
import com.lifelog.domain.specprofile.dto.SpecProfileScoreBand;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.service.SpecProfileSalaryScoreBandService;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecProfileSalaryScoreBandServiceTest {
    private final SpecProfileSalaryScoreBandService service = new SpecProfileSalaryScoreBandService();

    @ParameterizedTest
    @CsvSource({"3.49,4.5,3.0,3.5,false", "3.5,4.5,3.5,4.0,false", "4,4.5,4,4.5,true",
            "4,4,3.5,4,true", "4.5,4.5,4,4.5,true", "4.3,4.3,4,4.3,true",
            "89.99,100,80,90,false", "90,100,90,100,true", "100,100,90,100,true", "0,100,0,10,false"})
    @DisplayName("학점 구간은 경계와 만점을 정확하게 포함한다")
    void 학점경계_구간분류_성공(String grade, String scale, String lower, String upper, boolean inclusive) {
        // given / when
        SpecProfileScoreBand band = service.gradeBand(new BigDecimal(grade), new BigDecimal(scale));
        // then
        assertThat(band.lowerInclusive()).isEqualByComparingTo(lower);
        assertThat(band.upperBound()).isEqualByComparingTo(upper);
        assertThat(band.upperInclusive()).isEqualTo(inclusive);
    }

    @ParameterizedTest
    @CsvSource({"TOEIC,849,800,850", "TOEIC,850,850,900", "TOEIC,990,950,1000",
            "TOEFL,95,90,100", "TEPS,350,350,400", "IELTS,6.5,6,7",
            "TOEIC Speaking,155,140,160", "TOEIC Writing,180,180,200"})
    @DisplayName("시험별로 합의한 숫자 구간을 사용한다")
    void 시험별숫자구간_분류_성공(String exam, String score, String lower, String upper) {
        // given / when
        SpecProfileScoreBand band = service.englishBand(exam, new BigDecimal(score));
        // then
        assertThat(band.lowerInclusive()).isEqualByComparingTo(lower);
        assertThat(band.upperBound()).isEqualByComparingTo(upper);
        assertThat(band.upperInclusive()).isFalse();
    }

    @Test
    @DisplayName("미입력은 0으로 바꾸지 않고 등급과 기타 시험의 원값을 유지한다")
    void 미입력및등급_보존_성공() {
        // given
        SpecProfileSalaryClassificationData data = new SpecProfileSalaryClassificationData("133", "개발", List.of(),
                List.of(new LanguageCondition(5L, "OPIC", null, " ih ", null, null),
                        new LanguageCondition(8L, "기타", new BigDecimal("73"), null, null, null)), 0);
        // when
        SpecProfileSalaryClassificationData result = service.classify(data);
        // then
        assertThat(service.gradeBand(null, null)).isNull();
        assertThat(service.englishBand("TOEIC", null)).isNull();
        assertThat(result.languageExamScores().getFirst().gradeBand()).isEqualTo("IH");
        assertThat(result.languageExamScores().getFirst().scoreGrade()).isEqualTo(" ih ");
        assertThat(result.languageExamScores().get(1).scoreNumeric()).isEqualByComparingTo("73");
        assertThat(result.languageExamScores().get(1).numericBand()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"4.6,4.5", "-1,4.0", "3.5,5.0"})
    @DisplayName("학점 범위나 만점이 잘못되면 분류를 거절한다")
    void 잘못된학점_분류_실패(String grade, String scale) {
        // given / when / then
        assertThatThrownBy(() -> service.gradeBand(new BigDecimal(grade), new BigDecimal(scale)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.INVALID_CLASSIFICATION_SCORE));
    }
}
