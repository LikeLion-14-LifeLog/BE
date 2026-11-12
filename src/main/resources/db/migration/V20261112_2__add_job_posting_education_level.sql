-- 공고 최소 학력 단계 (서울 일자리포털 MIN_EDUBG_ICD)
-- 0 학력무관, 1 초졸이하, 2 중졸, 3 고졸, 4 대졸(2~3년), 5 대졸(4년), 6 석사, 7 박사
ALTER TABLE job_posting
    ADD COLUMN education_level SMALLINT NOT NULL DEFAULT 0;
