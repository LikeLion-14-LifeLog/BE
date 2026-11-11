-- 직무 그룹(jobGroup) 대신 직무 기준 데이터(한국고용직업분류 2025)의 중분류로 필터하므로 컬럼 삭제
ALTER TABLE job_posting
    DROP COLUMN job_group;
