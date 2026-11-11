package com.lifelog.domain.jobposting.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 맞춤 공고 직무 그룹. 어느 그룹에도 해당하지 않는 공고는 null (필터 없는 전체 조회에서만 노출)
// 판정 규칙은 service.JobGroupClassifier
@Getter
@RequiredArgsConstructor
public enum JobGroup {
    BACKEND("백엔드/서버"),
    FRONTEND("프론트엔드"),
    FULLSTACK("풀스택"),
    DATA_ENGINEER("데이터 엔지니어"),
    AI_ML("AI·머신러닝"),
    QA("QA·테스트"),
    SECURITY("보안"),
    EMBEDDED("임베디드"),
    DEVOPS_INFRA("DevOps·인프라");

    private final String label;
}
