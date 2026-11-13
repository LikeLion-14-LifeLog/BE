-- 연차별 금액은 기존 컬럼을 사용하고 AI 응답의 누락된 진단 문구만 추가한다.
-- 세 문구는 분석 공통 값이며 해당 실행의 각 연차 결과에 동일하게 저장한다.
-- 기존 결과에 없는 AI 응답은 임의로 복원하지 않고 null로 보존한다.
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN summary TEXT;
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN comparison_group_description TEXT;
ALTER TABLE spec_profile_analyzation_result
    ADD COLUMN recommendation TEXT;
