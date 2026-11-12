package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.entity.DesiredJob;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileQueryService {
    private final SpecProfileRepository profileRepository;
    private final SpecProfileMapper mapper;
    private final DesiredJobRepository desiredJobRepository;
    private final AcademicAbilityRepository academicRepository;
    private final AcademicAbilityMajorRepository academicMajorRepository;
    private final SpecProfileCertificateRepository certificateRepository;
    private final LanguageExamScoreRepository scoreRepository;
    private final ExperienceRepository experienceRepository;

    /**
     * 본인의 CURRENT와 모든 저장 항목을 동일한 스냅샷에서 조회한다.
     * @param memberId JWT로 인증된 회원 ID
     * @return 미입력 값과 빈 목록을 포함한 현재 스펙
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SpecProfileResDto getCurrentProfile(Long memberId) {
        log.info("[SpecProfileQueryService] 회원 스펙 조회 시작 | getCurrentProfile() - START | memberId: {}", memberId);
        /* (1) 인증된 회원의 유일한 CURRENT만 선택한다. */
        List<SpecProfile> profiles = profileRepository.findAllByMemberIdAndType(memberId, SpecProfileType.CURRENT);
        if (profiles.isEmpty()) {
            throw new CustomException(SpecProfileErrorCode.CURRENT_NOT_FOUND);
        }
        if (profiles.size() != 1) {
            throw new CustomException(SpecProfileErrorCode.INVALID_PROFILE_COUNT);
        }
        SpecProfile profile = profiles.getFirst();

        /* (2) 하위 목록과 표시용 참조를 일괄 조회하고 PK 순서를 고정한다. */
        DesiredJob desiredJob = desiredJobRepository.findByMemberId(memberId).orElse(null);
        Job job = desiredJob == null ? null : desiredJob.getJob();
        List<AcademicAbility> academics = academicRepository.findAllBySpecProfileId(profile.getId()).stream()
                .sorted(Comparator.comparing(AcademicAbility::getId)).toList();
        Map<Long, Major> majors = findAcademicMajors(academics);
        List<SpecProfileCertificate> certificates = certificateRepository.findAllBySpecProfileId(profile.getId()).stream()
                .sorted(Comparator.comparing(SpecProfileCertificate::getId)).toList();
        List<LanguageExamScore> scores = scoreRepository.findAllBySpecProfileId(profile.getId()).stream()
                .sorted(Comparator.comparing(LanguageExamScore::getId)).toList();
        List<Experience> experiences = experienceRepository.findAllBySpecProfileId(profile.getId()).stream()
                .sorted(Comparator.comparing(Experience::getId)).toList();

        /* (3) 트랜잭션 내에서 응답으로 변환하여 지연 로딩을 완료한다. */
        SpecProfileResDto result = mapper.toSpecProfileResDto(
                profile, desiredJob, job, academics, majors, certificates, scores, experiences);
        log.info("[SpecProfileQueryService] 회원 스펙 조회 종료 | getCurrentProfile() - END | profileId: {}", profile.getId());
        return result;
    }

    /**
     * 학력별 단일 전공을 일괄 조회하며 복수 연결은 무결성 오류로 처리한다.
     * @param academics 본인 프로필의 학력 목록
     * @return 학력 ID별 전공
     */
    private Map<Long, Major> findAcademicMajors(List<AcademicAbility> academics) {
        log.debug("[SpecProfileQueryService] 전공 조회 시작 | findAcademicMajors() - START");
        Map<Long, Major> result = new HashMap<>();
        if (!academics.isEmpty()) {
            List<AcademicAbilityMajor> links = academicMajorRepository.findAllByAcademicAbilityIdIn(
                    academics.stream().map(AcademicAbility::getId).toList());
            for (AcademicAbilityMajor link : links) {
                if (result.putIfAbsent(link.getAcademicAbility().getId(), link.getMajor()) != null) {
                    throw new CustomException(SpecProfileErrorCode.INVALID_MAJOR_COUNT);
                }
            }
        }
        log.debug("[SpecProfileQueryService] 전공 조회 종료 | findAcademicMajors() - END | count: {}", result.size());
        return result;
    }

}
