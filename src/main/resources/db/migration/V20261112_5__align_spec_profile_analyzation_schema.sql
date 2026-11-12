-- 과거 실행의 요청 시점 스냅샷은 현재 스펙으로 복원할 수 없다.
-- 기존 실행이 있으면 임의 백필이나 삭제 대신 별도의 데이터 이관을 요구한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM spec_profile_analyzation_run) THEN
        RAISE EXCEPTION '기존 스펙 분석 실행의 상태 및 요청 시점 스냅샷 이관이 필요합니다';
    END IF;
END $$;

-- PK 값, identity 및 결과 테이블의 기존 FK 관계를 유지한다.
ALTER TABLE spec_profile_analyzation_run
    RENAME COLUMN spec_profile_analyzation_run_id TO run_id;
ALTER TABLE spec_profile_analyzation_result
    RENAME COLUMN spec_profile_analyzation_result_id TO result_id;

ALTER TABLE spec_profile_analyzation_run DROP COLUMN model_version;
ALTER TABLE spec_profile_analyzation_result DROP COLUMN percentile;
ALTER TABLE spec_profile_analyzation_result ALTER COLUMN annual TYPE INTEGER;

ALTER TABLE spec_profile_analyzation_run
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN canceled_at TIMESTAMP,
    ADD COLUMN spec_profile_snapshot_json TEXT NOT NULL;
ALTER TABLE spec_profile_analyzation_run ALTER COLUMN job_id SET NOT NULL;
-- delete_at은 nullable로 유지하며 만료 시각을 자동 설정하지 않는다.
ALTER TABLE spec_profile_analyzation_run ADD CONSTRAINT ck_analyzation_run_status
    CHECK (status IN ('QUEUED', 'IN_PROGRESS', 'CANCELED', 'COMPLETED', 'FAILED'));
ALTER TABLE spec_profile_analyzation_run ADD CONSTRAINT ck_analyzation_run_canceled_at
    CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL));

-- 최신 실행 기준의 검사이며 동시 접수는 CURRENT 행 잠금으로 직렬화한다.
CREATE INDEX idx_analyzation_run_latest
    ON spec_profile_analyzation_run (spec_profile_id, created_at DESC, run_id DESC);
