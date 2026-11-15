package com.lifelog.domain.specprofile.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/** 고유 패널별로 선택한 연봉의 관측 통계. 금액 단위는 원이다. */
@Builder
public record KeepSalaryMatchedSampleStatistics(
        int sampleCount,
        BigDecimal annualSalaryMinKrw,
        BigDecimal annualSalaryMaxKrw,
        BigDecimal annualSalaryMeanKrw,
        List<BigDecimal> annualSalaryModesKrw,
        int modeFrequency,
        BigDecimal annualSalaryP25Krw,
        BigDecimal annualSalaryMedianKrw,
        BigDecimal annualSalaryP75Krw
) {}
