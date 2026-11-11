package com.lifelog.domain.jobposting.dto;

// 채용공고 수집 실행 상태 (서버 메모리 기준, 재시작하면 IDLE)
public enum JobPostingSyncState {
    IDLE, RUNNING, SUCCEEDED, FAILED
}
