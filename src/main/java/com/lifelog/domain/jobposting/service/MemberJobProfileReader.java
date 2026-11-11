package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 맞춤 공고 조건(희망 직무, 학력)을 회원 프로필에서 읽는다.
 * 목표 직무(spec_profile.target_job_id)는 V20261110_3에서 삭제되어 희망 직무만 사용한다.
 * 임시 저장 스펙도 포함한다.
 */
@Component
@RequiredArgsConstructor
public class MemberJobProfileReader {

    // 학력 유형 → 학력 단계 (공고 education_level과 같은 기준). ETC는 판단하지 않음
    // 재학·휴학·졸업유예도 해당 단계로 인정하므로 status는 보지 않음
    private static final Map<AcademicAbilityType, Integer> EDUCATION_LEVELS = Map.of(
            AcademicAbilityType.HIGH_SCHOOL, 3,
            AcademicAbilityType.COLLEGE, 4,
            AcademicAbilityType.UNIVERSITY, 5,
            AcademicAbilityType.GRADUATE_SCHOOL, 6,
            AcademicAbilityType.GRADUATE_SCHOOL_MASTER, 6,
            AcademicAbilityType.GRADUATE_SCHOOL_DOCTORAL, 7
    );

    private final DesiredJobRepository desiredJobRepository;
    private final SpecProfileRepository specProfileRepository;
    private final AcademicAbilityRepository academicAbilityRepository;

    @Transactional(readOnly = true)
    public MemberJobProfile read(Long memberId) {
        if (memberId == null) {
            return MemberJobProfile.empty();
        }
        return new MemberJobProfile(findSubCategoryCodes(memberId), findEducationLevel(memberId));
    }

    // 희망 직무의 소분류 코드 (미분류처럼 소분류가 없으면 빈 집합)
    private Set<String> findSubCategoryCodes(Long memberId) {
        return desiredJobRepository.findByMemberId(memberId)
                .map(desiredJob -> desiredJob.getJob())
                .map(Job::getSubCategoryCode)
                .map(Set::of)
                .orElse(Set.of());
    }

    // CURRENT 스펙의 학력 중 가장 높은 단계
    private Integer findEducationLevel(Long memberId) {
        return specProfileRepository.findAllByMemberIdAndType(memberId, SpecProfileType.CURRENT).stream()
                .map(SpecProfile::getId)
                .flatMap(profileId -> academicAbilityRepository.findAllBySpecProfileId(profileId).stream())
                .map(AcademicAbility::getType)
                .filter(Objects::nonNull)
                .map(EDUCATION_LEVELS::get)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }
}
