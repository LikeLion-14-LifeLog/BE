-- 기존 데이터 충돌은 제약 적용 시 실패시키며 임의 삭제하지 않는다.
ALTER TABLE spec_profile ADD COLUMN sort_order SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE spec_profile ALTER COLUMN type SET DEFAULT 'CURRENT';
ALTER TABLE spec_profile DROP CONSTRAINT spec_profile_member_id_type_key;

-- 최종 프로필 모델에서 사용하지 않는 과거 컬럼을 제거한다.
-- 값이 존재하면 데이터 손실 대신 마이그레이션을 중단한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM spec_profile WHERE target_option_key IS NOT NULL OR target_job_id IS NOT NULL) THEN
        RAISE EXCEPTION '사용 중인 TARGET 전용 컬럼의 데이터 이관이 필요합니다';
    END IF;
END $$;
ALTER TABLE spec_profile DROP COLUMN target_option_key;
ALTER TABLE spec_profile DROP COLUMN target_job_id;

ALTER TABLE desired_job DROP CONSTRAINT desired_job_member_id_job_id_key;
ALTER TABLE desired_job ADD CONSTRAINT uq_desired_job_member UNIQUE (member_id);
ALTER TABLE academic_ability ALTER COLUMN type TYPE VARCHAR(50);
ALTER TABLE academic_ability ADD COLUMN university_standard_grade NUMERIC(5,1);
ALTER TABLE language_exam_score ADD COLUMN expires_at DATE;
-- 참조 FK를 서로 교환하는 수정도 최종 상태를 기준으로 유일성을 검사한다.
ALTER TABLE spec_profile_certificate DROP CONSTRAINT spec_profile_certificate_spec_profile_id_certificate_id_key;
ALTER TABLE spec_profile_certificate ADD CONSTRAINT uq_spec_profile_certificate
    UNIQUE (spec_profile_id, certificate_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE language_exam_score ADD CONSTRAINT uq_spec_profile_language_exam
    UNIQUE (spec_profile_id, language_exam_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE experience ALTER COLUMN title TYPE VARCHAR(255);
ALTER TABLE experience ALTER COLUMN duration_months DROP NOT NULL;
