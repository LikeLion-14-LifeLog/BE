package com.lifelog.domain.specprofile.service.keep;

import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeepSalaryMatchedSampleStatisticsService {
    /**
     * 최소 10명의 고유 패널 표본을 검증하고 원 단위 통계를 계산한다.
     * @param samples 패널당 하나의 연도만 선택된 표본
     * @return 평균과 Type 7 보간 분위수를 소수 둘째 자리까지 계산한 통계
     */
    public KeepSalaryMatchedSampleStatistics calculate(List<KeepSalaryMatchedSample> samples) {
        log.info("[KeepSalaryMatchedSampleStatisticsService] 통계 계산 | calculate() - START");
        /* (1) 중복 패널, 누락값, 0 이하 연봉을 검증한다. */
        List<BigDecimal> salaries = validatedSalaries(samples);
        Map<BigDecimal, Integer> frequencies = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal salary : salaries) {
            frequencies.merge(salary, 1, Integer::sum);
            total = total.add(salary);
        }
        /* (2) 동률인 최빈값을 모두 보존하며, 모든 값이 한 번이면 최빈값을 비운다. */
        int modeFrequency = frequencies.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        List<BigDecimal> modes = modeFrequency == 1 ? List.of() : frequencies.entrySet().stream()
                .filter(entry -> entry.getValue() == modeFrequency).map(Map.Entry::getKey).toList();
        KeepSalaryMatchedSampleStatistics result = KeepSalaryMatchedSampleStatistics.builder()
                .sampleCount(salaries.size()).annualSalaryMinKrw(salaries.getFirst())
                .annualSalaryMaxKrw(salaries.getLast())
                .annualSalaryMeanKrw(total.divide(BigDecimal.valueOf(salaries.size()), 2, RoundingMode.HALF_UP))
                .annualSalaryModesKrw(modes).modeFrequency(modeFrequency)
                .annualSalaryP25Krw(quantile(salaries, new BigDecimal("0.25")))
                .annualSalaryMedianKrw(quantile(salaries, new BigDecimal("0.50")))
                .annualSalaryP75Krw(quantile(salaries, new BigDecimal("0.75"))).build();
        log.info("[KeepSalaryMatchedSampleStatisticsService] 통계 계산 | calculate() - END | count: {}", result.sampleCount());
        return result;
    }

    /**
     * 예측 연봉보다 높은 관측치와 동률의 절반으로 비교 표본 내 상위 비율을 계산한다.
     * @param predictedSalary 첫 취업 시점의 예측 연봉
     * @param samples 비교 표본
     * @return 소수 첫째 자리까지의 상위 비율. 전국 또는 신입 모집단의 순위를 의미하지 않는다.
     */
    public BigDecimal calculateTopPercent(BigDecimal predictedSalary, List<KeepSalaryMatchedSample> samples) {
        log.info("[KeepSalaryMatchedSampleStatisticsService] 표본 내 순위 계산 | calculateTopPercent() - START");
        if (predictedSalary == null || predictedSalary.signum() <= 0) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_SALARY_STATISTICS);
        }
        List<BigDecimal> salaries = validatedSalaries(samples);
        long higher = salaries.stream().filter(salary -> salary.compareTo(predictedSalary) > 0).count();
        long equal = salaries.stream().filter(salary -> salary.compareTo(predictedSalary) == 0).count();
        BigDecimal result = BigDecimal.valueOf(higher).add(BigDecimal.valueOf(equal).multiply(new BigDecimal("0.5")))
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(salaries.size()), 1, RoundingMode.HALF_UP);
        log.info("[KeepSalaryMatchedSampleStatisticsService] 표본 내 순위 계산 | calculateTopPercent() - END");
        return result;
    }

    /** 표본을 검증하고 숫자 비교 기준으로 정렬한다. */
    private List<BigDecimal> validatedSalaries(List<KeepSalaryMatchedSample> samples) {
        log.debug("[KeepSalaryMatchedSampleStatisticsService] 표본 검증 | validatedSalaries() - START");
        if (samples == null || samples.size() < 10) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INSUFFICIENT_MATCHED_SAMPLES);
        }
        Set<String> panelIds = new HashSet<>();
        List<BigDecimal> salaries = new ArrayList<>(samples.size());
        for (KeepSalaryMatchedSample sample : samples) {
            if (sample == null || sample.panelId() == null || sample.panelId().isBlank()
                    || !panelIds.add(sample.panelId()) || sample.annualSalaryKrw() == null
                    || sample.annualSalaryKrw().signum() <= 0) {
                throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_SALARY_STATISTICS);
            }
            salaries.add(sample.annualSalaryKrw().stripTrailingZeros());
        }
        salaries.sort(BigDecimal::compareTo);
        log.debug("[KeepSalaryMatchedSampleStatisticsService] 표본 검증 | validatedSalaries() - END");
        return salaries;
    }

    /** 정렬된 값에서 h=(N-1)p 위치를 선형 보간한다. */
    private BigDecimal quantile(List<BigDecimal> sorted, BigDecimal probability) {
        log.debug("[KeepSalaryMatchedSampleStatisticsService] 분위수 보간 | quantile() - START");
        BigDecimal position = BigDecimal.valueOf(sorted.size() - 1L).multiply(probability);
        int lower = position.intValue();
        int upper = Math.min(lower + 1, sorted.size() - 1);
        BigDecimal fraction = position.subtract(BigDecimal.valueOf(lower));
        BigDecimal result = sorted.get(lower).add(sorted.get(upper).subtract(sorted.get(lower)).multiply(fraction))
                .setScale(2, RoundingMode.HALF_UP);
        log.debug("[KeepSalaryMatchedSampleStatisticsService] 분위수 보간 | quantile() - END");
        return result;
    }
}
