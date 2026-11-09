-- 빈 전공 테이블에서도 동시 초기화를 직렬화하기 위한 단일 잠금 행
CREATE TABLE major_init_lock
(
    id INTEGER PRIMARY KEY CHECK (id = 1)
);

INSERT INTO major_init_lock (id) VALUES (1);
