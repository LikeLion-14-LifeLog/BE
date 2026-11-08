-- 미분류(9999)는 대분류 번호와 중·소분류가 없으므로 NULL 허용 (main_category_name은 NOT NULL 유지)
ALTER TABLE job
    ALTER COLUMN main_category_code DROP NOT NULL,
    ALTER COLUMN middle_category_code DROP NOT NULL,
    ALTER COLUMN middle_category_name DROP NOT NULL,
    ALTER COLUMN sub_category_code DROP NOT NULL,
    ALTER COLUMN sub_category_name DROP NOT NULL;
