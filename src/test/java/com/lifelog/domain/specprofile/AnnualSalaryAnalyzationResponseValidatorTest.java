package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto.SalaryPrediction;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAiResDto;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.validation.AnnualSalaryAnalyzationResponseValidator;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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

    @ParameterizedTest
    @MethodSource("validSimulationDetails")
    @DisplayName("짧은 기간·비용 표기와 해요체 직무 활용도는 카드에 표시할 수 있다")
    void 간결한준비정보_검증_성공(SpecProfileSimulationAiResDto response) {
        // given / when / then
        assertThatCode(() -> validator.validateSimulationDetails(response)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("invalidSimulationDetails")
    @DisplayName("장문·여러 줄 설명과 해요체가 아닌 판단 기준은 카드 결과로 반환하지 않는다")
    void 카드표시형식위반_검증_실패(SpecProfileSimulationAiResDto response) {
        // given / when / then
        assertThatThrownBy(() -> validator.validateSimulationDetails(response))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.INVALID_AI_RESPONSE));
    }

    private static Stream<SpecProfileSimulationAiResDto> validSimulationDetails() {
        return Stream.of(simulationDetails("약 2~3개월", "약 0~15만 원", "실무 경험을 중시하는 채용에 적합해요."),
                simulationDetails("약 2년 (4학기 기준)", "약 1,200만 원", "연구개발 직무 지원에 적합해요."),
                simulationDetails("약 6개월", "비용 확인이 필요해요.", "실무 역량을 보완할 수 있어요."),
                simulationDetails("추가 준비 없음", "추가 비용 없음", "현재 보유 스펙을 기준으로 분석했어요."));
    }

    private static Stream<SpecProfileSimulationAiResDto> invalidSimulationDetails() {
        return Stream.of(null,
                simulationDetails(null, "약 15만 원", "실무 역량 보완에 적합해요."),
                simulationDetails("약 6개월", " ", "실무 역량 보완에 적합해요."),
                simulationDetails("약 6개월", "약 15만 원", null),
                simulationDetails("약 6개월", "약 15만 원", "실무 역량 보완에 적합합니다."),
                simulationDetails("약 6개월\n병행 준비 가정", "약 15만 원", "실무 역량 보완에 적합해요."),
                simulationDetails("약 6개월", "약 15만 원\n장비 비용 제외", "실무 역량 보완에 적합해요."),
                simulationDetails("약 6개월", "약 15만 원", "실무 경험을 쌓아요.\n채용에 적합해요."),
                simulationDetails("기".repeat(31), "약 15만 원", "실무 역량 보완에 적합해요."),
                simulationDetails("약 6개월", "참고 추정 약 0~150,000원. 기존 개발용 컴퓨터와 무료 도구를 사용하고 유료 교육 없이 진행하는 가정이며 기존 학력·자격증·어학·프로젝트 비용은 포함하지 않았다.", "실무 역량 보완에 적합해요."),
                simulationDetails("약 6개월", "약 15만 원", "설".repeat(60) + "요"));
    }

    private static SpecProfileSimulationAiResDto simulationDetails(String period, String cost, String criteria) {
        return SpecProfileSimulationAiResDto.builder().preparationPeriod(period).estimatedCost(cost)
                .decisionCriteria(criteria).build();
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
