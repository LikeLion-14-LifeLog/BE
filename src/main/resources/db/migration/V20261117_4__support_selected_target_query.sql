-- 기존 TARGET은 원래 추가 조건을 정확히 복원할 수 없어 재저장 전까지 미기록 상태를 유지한다.
ALTER TABLE spec_profile ADD COLUMN target_company_group_scale VARCHAR(20);
ALTER TABLE spec_profile ADD COLUMN target_conditions_recorded BOOLEAN NOT NULL DEFAULT FALSE;

-- CURRENT에서 복제한 항목과 사용자 추가 목표 항목을 구분한다.
ALTER TABLE academic_ability ADD COLUMN target_additional BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE spec_profile_certificate ADD COLUMN target_additional BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE language_exam_score ADD COLUMN target_additional BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE experience ADD COLUMN target_additional BOOLEAN NOT NULL DEFAULT FALSE;

-- 회원별 CURRENT 및 선택 TARGET 조회에 같은 선행 컬럼 인덱스를 사용한다.
CREATE INDEX idx_spec_profile_member_type_selected ON spec_profile (member_id, type, target_spec_selected);
