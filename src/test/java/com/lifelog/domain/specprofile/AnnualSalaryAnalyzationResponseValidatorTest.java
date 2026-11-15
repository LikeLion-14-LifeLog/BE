package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto.SalaryPrediction;
import com.lifelog.domain.specprofile.service.AnnualSalaryAnalyzationResponseValidator;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnualSalaryAnalyzationResponseValidatorTest {
    private final AnnualSalaryAnalyzationResponseValidator validator = new AnnualSalaryAnalyzationResponseValidator();

    @Test
    @DisplayName("0·3·5년차가 각각 한 번이고 금액 순서가 유효하면 응답을 허용한다")
    void 유효한예측_검증_성공() {
        // given / when / then
        assertThatCode(() -> validator.validate(response(List.of(prediction(0), prediction(3), prediction(5)))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("invalidPredictions")
    @DisplayName("연차 중복·누락·미지원 값과 금액 누락·역전·음수는 허용하지 않는다")
    void 잘못된연차또는금액_검증_실패(List<SalaryPrediction> predictions) {
        // given / when / then
        assertThatThrownBy(() -> validator.validate(response(predictions))).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("필수 설명이 비어 있으면 응답을 허용하지 않는다")
    void 설명누락_검증_실패() {
        // given
        AnnualSalaryAnalyzationAiResDto response = new AnnualSalaryAnalyzationAiResDto(" ", List.of(prediction(0), prediction(3), prediction(5)), "집단", "보완점");
        // when / then
        assertThatThrownBy(() -> validator.validate(response)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(CustomException.class);
    }

    private static Stream<List<SalaryPrediction>> invalidPredictions() {
        return Stream.of(List.of(prediction(0), prediction(0), prediction(5)), List.of(prediction(0), prediction(3)),
                List.of(prediction(0), prediction(3), prediction(9)),
                List.of(new SalaryPrediction(0, 30L, 20L, 40L), prediction(3), prediction(5)),
                List.of(new SalaryPrediction(0, -1L, 20L, 40L), prediction(3), prediction(5)),
                List.of(new SalaryPrediction(0, 10L, null, 40L), prediction(3), prediction(5)),
                Arrays.asList(null, prediction(3), prediction(5)));
    }

    private static SalaryPrediction prediction(int annual) {
        return new SalaryPrediction(annual, 34000000L, 36000000L, 38000000L);
    }

    private AnnualSalaryAnalyzationAiResDto response(List<SalaryPrediction> predictions) {
        return new AnnualSalaryAnalyzationAiResDto("요약", predictions, "집단", "보완점");
    }
}
