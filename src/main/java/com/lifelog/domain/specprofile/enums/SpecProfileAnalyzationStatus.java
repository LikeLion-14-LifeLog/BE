package com.lifelog.domain.specprofile.enums;

public enum SpecProfileAnalyzationStatus {
    QUEUED, IN_PROGRESS, CANCELED, COMPLETED, FAILED;

    /** 분석 실행 중 여부를 반환한다. @return 대기 또는 진행 중 여부 */
    public boolean isRunning() {
        return this == QUEUED || this == IN_PROGRESS;
    }
}
