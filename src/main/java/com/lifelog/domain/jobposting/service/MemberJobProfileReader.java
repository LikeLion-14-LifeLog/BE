package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 맞춤 공고 조건(희망 직무, 학력)을 회원 프로필에서 읽는다.
 * 목표 직무(spec_profile.target_job_id)는 V20261110_3에서 삭제되어 희망 직무만 사용한다.
 * 임시 저장 스펙도 포함한다.
 */
@Component
@RequiredArgsConstructor
public class MemberJobProfileReader {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 재학·휴학은 졸업예정 연월이 오늘부터 이 개월 수 이내일 때만 그 단계로 인정
    private static final int ATTENDING_RECOGNITION_MONTHS = 12;

    // 인정되는 학력이 하나도 없을 때 쓰는 단계 (고졸, 학력 조건이 고졸 이하·무관인 공고만 노출)
    private static final int FALLBACK_EDUCATION_LEVEL = 3;

    // 학력 유형 → 학력 단계 (공고 education_level과 같은 기준). ETC는 판단하지 않음
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

    // CURRENT 스펙의 학력 중 인정되는 가장 높은 단계
    // 판단할 학력(ETC 제외)이 없으면 null, 있는데 모두 미인정이면 고졸 단계
    private Integer findEducationLevel(Long memberId) {
        List<AcademicAbility> abilities = specProfileRepository
                .findAllByMemberIdAndType(memberId, SpecProfileType.CURRENT).stream()
                .map(SpecProfile::getId)
                .flatMap(profileId -> academicAbilityRepository.findAllBySpecProfileId(profileId).stream())
                .filter(ability -> ability.getType() != null && EDUCATION_LEVELS.containsKey(ability.getType()))
                .toList();
        if (abilities.isEmpty()) {
            return null;
        }

        YearMonth thisMonth = YearMonth.now(SEOUL);
        return abilities.stream()
                .filter(ability -> isRecognized(ability, thisMonth))
                .map(ability -> EDUCATION_LEVELS.get(ability.getType()))
                .max(Integer::compareTo)
                .orElse(FALLBACK_EDUCATION_LEVEL);
    }

    // 졸업·졸업유예는 인정. 재학·휴학은 졸업예정 연월이 12개월 이내(이미 지난 경우 포함)일 때만 인정
    private boolean isRecognized(AcademicAbility ability, YearMonth thisMonth) {
        AcademicAbilityStatus status = ability.getStatus();
        if (status == AcademicAbilityStatus.GRADUATED || status == AcademicAbilityStatus.GRADUATE_POSTPONED) {
            return true;
        }
        if (status != AcademicAbilityStatus.ATTENDING && status != AcademicAbilityStatus.LEAVE_FROM_SCHOOL) {
            return false;
        }
        LocalDate graduatedAt = ability.getGraduatedAt();
        if (graduatedAt == null) {
            return false;
        }
        return !YearMonth.from(graduatedAt).isAfter(thisMonth.plusMonths(ATTENDING_RECOGNITION_MONTHS));
    }
}
