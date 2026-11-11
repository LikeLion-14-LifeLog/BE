-- 직무 기준 데이터를 한국고용직업분류 2018 → 2025 개정으로 교체하기 위해 기존 직무를 모두 지운다.
-- 이유
-- - 채용공고 원본(고용24 직종코드)이 2025 개정 기준이라, 직무를 2025로 맞춰야 앞 4자리로 바로 연결할 수 있음
-- - 2018과 2025는 같은 코드라도 의미가 다른 경우가 있어(예: 0262 인사·교육·훈련 사무원 → 영업 및 마케팅 사무원)
--   기존 행을 코드 기준으로 덮어쓰면 회원이 고른 직무의 의미가 조용히 바뀜
-- - 출시 전이라 희망·목표 직무 등 직무를 참조하는 데이터는 지워도 됨 (데이터 손실 감수)
-- 적용 후 POST /api/admin/jobs/init 으로 2025 직무(data/job_2025.csv)를 다시 등록해야 함

-- 1) job을 참조하는 데이터 정리 (V1__init.sql 기준 4곳)
DELETE FROM desired_job;
DELETE FROM job_posting_job;
UPDATE spec_profile SET target_job_id = NULL WHERE target_job_id IS NOT NULL;
UPDATE spec_profile_analyzation_run SET job_id = NULL WHERE job_id IS NOT NULL;

-- 2) 2018 직무 전체 삭제
DELETE FROM job;
