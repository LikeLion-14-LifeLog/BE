package com.lifelog.domain.major.dto;

import java.util.List;

public record MajorCsvReadResult(int sourceRowCount, int duplicateRowCount, List<MajorCsvRow> rows) {
    public MajorCsvReadResult {
        rows = List.copyOf(rows);
    }
}
