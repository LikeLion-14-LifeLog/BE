-- 카드 표시용 근무 지역명 (서울 일자리포털 REGION, 예: "서울 강남구")
ALTER TABLE job_posting
    ADD COLUMN region_name VARCHAR(50);
