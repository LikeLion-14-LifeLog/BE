package com.lifelog.domain.specprofile.dto;

import java.math.BigDecimal;
import java.util.List;

/** 한 패널에서 선택한 단 하나의 연도. 연봉은 그 연도의 원본 값만 사용한다. */
public record KeepSalaryMatchedSample(String panelId, int selectedYear, BigDecimal annualSalaryKrw,
                                     KeepSalaryAccumulatedSpec accumulatedSpec, int similarityScore, int directMatchCount,
                                     List<MatchEvidence> matchedConditions) {
    public record MatchEvidence(String condition, int availableYear) {}
}
