package com.lifelog.domain.jobposting.service;

import java.util.Set;

/**
 * 맞춤 공고에 쓰는 회원 조건.
 * @param subCategoryCodes 희망 직무(desired_job) + 목표 직무(TARGET 스펙의 target_job_id)의 소분류 코드(3자리)
 * @param educationLevel   회원 학력 단계 (3 고졸, 4 전문대, 5 대학교, 6 대학원), 학력 정보가 없으면 null
 */
public record MemberJobProfile(Set<String> subCategoryCodes, Integer educationLevel) {

    public static MemberJobProfile empty() {
        return new MemberJobProfile(Set.of(), null);
    }

    // 희망·목표 직무가 없으면(미분류처럼 소분류가 없는 직무만 있어도) 프로필 없음으로 보고 맞춤 조건을 적용하지 않음
    public boolean isPersonalizable() {
        return !subCategoryCodes.isEmpty();
    }
}
