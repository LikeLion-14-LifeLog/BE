package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryMatchedSampleStatisticsService;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeepSalaryMatchedSampleStatisticsServiceTest {
    private final KeepSalaryMatchedSampleStatisticsService service = new KeepSalaryMatchedSampleStatisticsService();

    @Test
    @DisplayName("최소·최대·평균과 Type 7 분위수를 정확히 계산하고 단일 빈도 최빈값은 비운다")
    void 기본통계와보간분위수_계산_성공() {
        // given
        List<KeepSalaryMatchedSample> samples = samples("10", "20", "30", "40", "50", "60", "70", "80", "90", "100");
        // when
        KeepSalaryMatchedSampleStatistics result = service.calculate(samples);
        // then
        assertThat(result.sampleCount()).isEqualTo(10);
        assertThat(result.annualSalaryMinKrw()).isEqualByComparingTo("10");
        assertThat(result.annualSalaryMaxKrw()).isEqualByComparingTo("100");
        assertThat(result.annualSalaryMeanKrw()).isEqualByComparingTo("55.00");
        assertThat(result.annualSalaryP25Krw()).isEqualByComparingTo("32.50");
        assertThat(result.annualSalaryMedianKrw()).isEqualByComparingTo("55.00");
        assertThat(result.annualSalaryP75Krw()).isEqualByComparingTo("77.50");
        assertThat(result.annualSalaryModesKrw()).isEmpty();
        assertThat(result.modeFrequency()).isEqualTo(1);
    }

    @Test
    @DisplayName("소수점 자릿수가 다른 동일 금액을 합산하고 공동 최빈값을 모두 반환한다")
    void 금액정규화와공동최빈값_계산_성공() {
        // given / when
        KeepSalaryMatchedSampleStatistics result = service.calculate(samples("10", "10.00", "20", "20.0", "30", "40", "50", "60", "70", "80"));
        // then
        assertThat(result.modeFrequency()).isEqualTo(2);
        assertThat(result.annualSalaryModesKrw()).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("10"), new BigDecimal("20"));
    }

    @Test
    @DisplayName("평균 계산에는 BigDecimal을 사용하고 소수 둘째 자리에서 반올림한다")
    void 소수원화평균_계산_성공() {
        // given / when
        KeepSalaryMatchedSampleStatistics result = service.calculate(samples("1.05", "2", "2", "2", "2", "2", "2", "2", "2", "2"));
        // then
        assertThat(result.annualSalaryMeanKrw()).isEqualByComparingTo("1.91");
    }

    @Test
    @DisplayName("동률의 절반을 반영한 표본 내 상위 비율과 양 끝 경계를 계산한다")
    void 표본내상위비율_계산_성공() {
        // given
        List<KeepSalaryMatchedSample> samples = samples("10", "20", "30", "40", "50", "60", "70", "80", "90", "100");
        // when / then
        assertThat(service.calculateTopPercent(new BigDecimal("50"), samples)).isEqualByComparingTo("55.0");
        assertThat(service.calculateTopPercent(new BigDecimal("1"), samples)).isEqualByComparingTo("100.0");
        assertThat(service.calculateTopPercent(new BigDecimal("101"), samples)).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("패널 중복은 연도나 연봉이 달라도 통계 표본으로 허용하지 않는다")
    void 중복패널_계산_실패() {
        // given
        List<KeepSalaryMatchedSample> samples = new ArrayList<>(samples("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        samples.set(9, new KeepSalaryMatchedSample("panel-0", 2020, BigDecimal.TEN, null, 0, 0, List.of()));
        // when / then
        assertThatThrownBy(() -> service.calculate(samples)).isInstanceOfSatisfying(CustomException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.INVALID_SALARY_STATISTICS));
    }

    @Test
    @DisplayName("10명 미만과 0 이하 연봉은 통계를 생성하지 않는다")
    void 부족하거나잘못된표본_계산_실패() {
        // given / when / then
        assertThatThrownBy(() -> service.calculate(samples("1", "2"))).isInstanceOfSatisfying(CustomException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.INSUFFICIENT_MATCHED_SAMPLES));
        assertThatThrownBy(() -> service.calculate(samples("0", "2", "3", "4", "5", "6", "7", "8", "9", "10")))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.INVALID_SALARY_STATISTICS));
    }

    private List<KeepSalaryMatchedSample> samples(String... salaries) {
        return IntStream.range(0, salaries.length).mapToObj(index -> new KeepSalaryMatchedSample(
                "panel-" + index, 2024, new BigDecimal(salaries[index]), null, 0, 0, List.of())).toList();
    }
}
