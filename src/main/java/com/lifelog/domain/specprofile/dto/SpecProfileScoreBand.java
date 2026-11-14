package com.lifelog.domain.specprofile.dto;

import java.math.BigDecimal;

/** 같은 점수 체계에서 하한을 포함하는 점수 구간. 상한 포함 여부는 별도로 보존한다. */
public record SpecProfileScoreBand(BigDecimal lowerInclusive, BigDecimal upperBound, boolean upperInclusive) {}
