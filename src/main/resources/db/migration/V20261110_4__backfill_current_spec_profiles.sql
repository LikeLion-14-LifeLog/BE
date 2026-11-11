-- type과 sort_order는 테이블 기본값을 사용한다.
INSERT INTO spec_profile (member_id)
SELECT m.member_id
FROM member m
WHERE NOT EXISTS (
    SELECT 1 FROM spec_profile p WHERE p.member_id = m.member_id AND p.type = 'CURRENT'
);
