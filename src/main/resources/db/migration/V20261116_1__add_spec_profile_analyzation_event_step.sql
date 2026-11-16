-- NULL은 실행 전체 상태 로그이며, 값이 있으면 해당 단계의 상태 로그이다.
-- 기존 이벤트의 단계는 임의로 복원하지 않는다.
ALTER TABLE spec_profile_analyzation_event_log ADD COLUMN step VARCHAR(64);
ALTER TABLE spec_profile_analyzation_event_log ADD CONSTRAINT ck_analyzation_event_step
    CHECK (step IN ('RUN_START', 'SNAPSHOT_LOAD', 'CLASSIFICATION_DATA_LOAD',
        'SCORE_BAND_CLASSIFICATION', 'SAMPLE_SELECTION', 'STATISTICS_AND_PROMPT_BUILD',
        'SALARY_PREDICTION', 'RESULT_COMPOSITION', 'RESULT_SAVE'));
