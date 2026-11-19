package com.lifelog.domain.specprofile.dto;

import com.lifelog.global.infra.openai.OpenApiPrompt;

import java.util.List;

/** AI 호출 전에 서버가 확정한 스냅샷·비교 표본·통계와 예측 입력. */
public record SpecProfileSimulationPreparedAnalysis(
        SpecProfileAnalyzationSnapshot snapshot,
        SpecProfileSalaryClassificationData classified,
        List<KeepSalaryMatchedSample> samples,
        KeepSalaryMatchedSampleStatistics statistics,
        OpenApiPrompt prompt
) {}
