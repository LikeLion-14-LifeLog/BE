-- 미래 목표의 미정 취득일을 허용한다. CURRENT의 필수 날짜는 기존 요청 검증으로 유지한다.
ALTER TABLE spec_profile_certificate ALTER COLUMN acquired_at DROP NOT NULL;
ALTER TABLE language_exam_score ALTER COLUMN acquired_at DROP NOT NULL;

-- 기존 시뮬레이터가 허용하는 100점 만점 학점도 TARGET에 보존한다.
ALTER TABLE academic_ability ALTER COLUMN university_grade TYPE NUMERIC(5, 2);
