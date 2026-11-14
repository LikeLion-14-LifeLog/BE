-- 분석 당시의 관측 통계, 비교 집단과 표시 기준을 보존한다.
-- 기존 결과의 누락값은 재분석 없이 복원할 수 없으므로 NULL로 유지한다.
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN top_percent NUMERIC(4, 1);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN job_sub_category_code VARCHAR(255);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN job_sub_category_name VARCHAR(255);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN sample_count INTEGER;
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN annual_salary_min_krw NUMERIC(19, 2);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN annual_salary_max_krw NUMERIC(19, 2);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN annual_salary_mean_krw NUMERIC(19, 2);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN mode_frequency INTEGER;
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN annual_salary_p25_krw NUMERIC(19, 2);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN annual_salary_median_krw NUMERIC(19, 2);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN annual_salary_p75_krw NUMERIC(19, 2);
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN salary_notice TEXT;

CREATE TABLE spec_profile_analyzation_result_salary_mode
(
    result_id BIGINT NOT NULL REFERENCES spec_profile_analyzation_result (result_id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    annual_salary_krw NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (result_id, sort_order)
);

CREATE TABLE spec_profile_analyzation_result_observation_year
(
    result_id BIGINT NOT NULL REFERENCES spec_profile_analyzation_result (result_id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    observation_year INTEGER NOT NULL,
    PRIMARY KEY (result_id, sort_order)
);
