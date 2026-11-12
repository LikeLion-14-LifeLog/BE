package com.lifelog.domain.specprofile.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SpecProfileAnalyzationStep {
    INPUT_PREPARATION(1, "저장된 스펙 확인 및 분석 입력 생성"),
    SALARY_PREDICTION(2, "스펙 기반 연봉 분석"),
    RESULT_SAVE(3, "분석 결과 저장");

    private final int order;
    private final String displayName;
}
