package com.lifelog.domain.major.dto;

import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.enums.MajorMiddleField;
import com.lifelog.domain.major.enums.MajorSubField;

public record MajorCsvRow(
        DegreeProcess degreeProcess,
        MajorMainField mainField,
        MajorMiddleField middleField,
        MajorSubField subField,
        String name,
        String code
) {
}
