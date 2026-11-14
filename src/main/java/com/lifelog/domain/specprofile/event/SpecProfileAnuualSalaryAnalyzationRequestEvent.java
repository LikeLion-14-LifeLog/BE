package com.lifelog.domain.specprofile.event;

import com.lifelog.domain.specprofile.dto.OpenApiRequest;

/** 커밋된 분석 실행 ID를 전달하는 연봉 분석 요청 이벤트. */
public record SpecProfileAnuualSalaryAnalyzationRequestEvent(Long runId) implements OpenApiRequest {
}
