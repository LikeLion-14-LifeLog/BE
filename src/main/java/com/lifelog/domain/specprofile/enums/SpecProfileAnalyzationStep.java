package com.lifelog.domain.specprofile.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SpecProfileAnalyzationStep {
    RUN_START(1, "분석 실행 시작 및 상태 변경"),
    SNAPSHOT_LOAD(2, "저장된 스펙 조회"),
    CLASSIFICATION_DATA_LOAD(3, "표본 분류 조건 조회"),
    SCORE_BAND_CLASSIFICATION(4, "학점 및 어학 성적 구간 분류"),
    SAMPLE_SELECTION(5, "누적 스펙 기반 비교 표본 추출"),
    STATISTICS_AND_PROMPT_BUILD(6, "연봉 통계 계산 및 분석 프롬프트 생성"),
    SALARY_PREDICTION(7, "AI 연봉 분석 및 응답 검증"),
    RESULT_COMPOSITION(8, "상위 비율 계산 및 분석 결과 구성"),
    RESULT_SAVE(9, "분석 결과 저장 및 완료 처리");

    private final int order;
    private final String displayName;
}
