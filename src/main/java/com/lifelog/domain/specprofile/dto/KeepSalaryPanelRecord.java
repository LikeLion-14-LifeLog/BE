package com.lifelog.domain.specprofile.dto;

import java.util.List;
import java.util.Map;

/** CSV 한 행과 각 조사 연도의 원본 값. 숫자 코드의 앞자리 0을 보존한다. */
public record KeepSalaryPanelRecord(String panelId, List<YearData> years) {
    public record YearData(int year, Map<String, String> values, Map<String, List<String>> history) {}
}
