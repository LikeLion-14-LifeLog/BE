package com.lifelog.domain.jobposting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "채용공고 수집 실행 상태 (마지막 실행 기준)")
public record JobPostingSyncStatusResponse(
        @Schema(description = "IDLE: 서버 시작 후 실행 이력 없음, RUNNING: 실행 중, SUCCEEDED: 성공, FAILED: 실패", example = "SUCCEEDED")
        JobPostingSyncState status,
        @Schema(description = "실행 시작 시각 (Asia/Seoul)", example = "2026-11-12T04:00:00", nullable = true)
        LocalDateTime startedAt,
        @Schema(description = "실행 종료 시각 (Asia/Seoul), 실행 중이면 null", example = "2026-11-12T04:03:12", nullable = true)
        LocalDateTime finishedAt,
        @Schema(description = "수집 결과 (성공했을 때만)", nullable = true)
        JobPostingSyncResponse result,
        @Schema(description = "실패 원인 에러 코드 (실패했을 때만, 예: JOB_POSTING_502)", example = "JOB_POSTING_502", nullable = true)
        String errorCode
) {

    public static JobPostingSyncStatusResponse idle() {
        return new JobPostingSyncStatusResponse(JobPostingSyncState.IDLE, null, null, null, null);
    }

    public static JobPostingSyncStatusResponse running(LocalDateTime startedAt) {
        return new JobPostingSyncStatusResponse(JobPostingSyncState.RUNNING, startedAt, null, null, null);
    }

    public static JobPostingSyncStatusResponse succeeded(LocalDateTime startedAt, LocalDateTime finishedAt,
                                                         JobPostingSyncResponse result) {
        return new JobPostingSyncStatusResponse(JobPostingSyncState.SUCCEEDED, startedAt, finishedAt, result, null);
    }

    public static JobPostingSyncStatusResponse failed(LocalDateTime startedAt, LocalDateTime finishedAt,
                                                      String errorCode) {
        return new JobPostingSyncStatusResponse(JobPostingSyncState.FAILED, startedAt, finishedAt, null, errorCode);
    }
}
