CREATE TABLE language_exam (
    language_exam_id BIGINT      PRIMARY KEY,
    name             VARCHAR(50) NOT NULL UNIQUE,
    language_type    VARCHAR(20) NOT NULL,
    score_type       VARCHAR(20) NOT NULL
);
