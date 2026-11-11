package com.lifelog.domain.jobposting.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 맞춤 공고 조건(희망·목표 직무, 학력)을 회원 프로필 테이블에서 읽는다.
 * desired_job·spec_profile·academic_ability는 아직 엔티티가 없어 네이티브 쿼리로 조회한다.
 * 임시 저장(temporary_stored=true) 스펙도 포함한다.
 */
@Component
@RequiredArgsConstructor
public class MemberJobProfileReader {

    // academic_ability.type → 학력 단계 (공고 education_level과 같은 기준). ETC는 판단하지 않음
    // 재학·휴학·졸업유예도 해당 단계로 인정하므로 status는 보지 않음
    private static final Map<String, Integer> EDUCATION_LEVELS = Map.of(
            "HIGH_SCHOOL", 3,
            "COLLEGE", 4,
            "UNIVERSITY", 5,
            "GRADUATE_SCHOOL", 6
    );

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public MemberJobProfile read(Long memberId) {
        if (memberId == null) {
            return MemberJobProfile.empty();
        }
        return new MemberJobProfile(findJobIds(memberId), findEducationLevel(memberId));
    }

    private Set<Long> findJobIds(Long memberId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT dj.job_id FROM desired_job dj WHERE dj.member_id = :memberId
                        UNION
                        SELECT sp.target_job_id FROM spec_profile sp
                         WHERE sp.member_id = :memberId AND sp.type = 'TARGET' AND sp.target_job_id IS NOT NULL
                        """)
                .setParameter("memberId", memberId)
                .getResultList();
        Set<Long> jobIds = new HashSet<>();
        rows.stream().filter(Objects::nonNull).forEach(id -> jobIds.add(((Number) id).longValue()));
        return jobIds;
    }

    // CURRENT 스펙의 학력 중 가장 높은 단계
    private Integer findEducationLevel(Long memberId) {
        List<?> types = entityManager.createNativeQuery("""
                        SELECT aa.type FROM academic_ability aa
                          JOIN spec_profile sp ON sp.spec_profile_id = aa.spec_profile_id
                         WHERE sp.member_id = :memberId AND sp.type = 'CURRENT'
                        """)
                .setParameter("memberId", memberId)
                .getResultList();
        return types.stream()
                .map(type -> EDUCATION_LEVELS.get(String.valueOf(type)))
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }
}
