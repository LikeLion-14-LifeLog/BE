-- 중계열 추가
ALTER TABLE major
    ADD COLUMN middle_field VARCHAR(100) NOT NULL;

-- 소계열 추가
ALTER TABLE major
    ADD COLUMN sub_field VARCHAR(100) NOT NULL;

-- ENUM 문자열 길이를 고려한 컬럼 크기 변경
ALTER TABLE major
ALTER COLUMN main_field TYPE VARCHAR(50);

ALTER TABLE major
ALTER COLUMN degree_process TYPE VARCHAR(50);