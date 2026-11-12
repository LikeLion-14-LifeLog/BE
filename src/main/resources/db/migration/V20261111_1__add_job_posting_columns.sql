-- 채용공고: 지원 링크, 직무 그룹, 학력 조건, 저장 수, 원본 직종 코드, 근무 시간, 복리후생, 전형·접수 방법
ALTER TABLE job_posting
    ADD COLUMN apply_url             VARCHAR(500),
    ADD COLUMN job_group             VARCHAR(30),
    ADD COLUMN education_requirement VARCHAR(50),
    ADD COLUMN bookmark_count        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN external_job_code     VARCHAR(10),
    ADD COLUMN work_schedule         VARCHAR(255),
    ADD COLUMN benefits              TEXT,
    ADD COLUMN application_method    TEXT;

-- 기업: 서울 일자리포털은 업종명만 제공하므로 코드는 nullable, 업종명 추가
ALTER TABLE company
    ALTER COLUMN industry_code DROP NOT NULL,
    ADD COLUMN industry_name VARCHAR(100);

-- 기업은 고유 키가 없어 name으로 조회
CREATE INDEX idx_company_name ON company (name);
