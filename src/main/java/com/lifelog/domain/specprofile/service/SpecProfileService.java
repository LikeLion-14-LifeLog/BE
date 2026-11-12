package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.entity.DesiredJob;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.AcademicAbilityMajor;
import com.lifelog.domain.specprofile.entity.Experience;
import com.lifelog.domain.specprofile.entity.LanguageExamScore;
import com.lifelog.domain.specprofile.entity.SpecProfileCertificate;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.AcademicAbilityMajorRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileCertificateRepository;
import com.lifelog.domain.specprofile.repository.LanguageExamScoreRepository;
import com.lifelog.domain.specprofile.repository.ExperienceRepository;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.major.entity.Major;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileService {
    private final MemberRepository memberRepository;
    private final SpecProfileRepository profileRepository;
    private final SpecProfileMapper mapper;
    private final SpecProfileCreationPolicy creationPolicy;
    private final SpecProfileSaveService specProfileSaveService;
    private final DesiredJobRepository desiredJobRepository;
    private final AcademicAbilityRepository academicRepository;
    private final AcademicAbilityMajorRepository academicMajorRepository;
    private final SpecProfileCertificateRepository certificateRepository;
    private final LanguageExamScoreRepository scoreRepository;
    private final ExperienceRepository experienceRepository;
    private final JobRepository jobRepository;

    /**
     * 본인의 CURRENT와 모든 저장 항목을 동일한 스냅샷에서 조회한다.
     * @param memberId JWT로 인증된 회원 ID
     * @return 미입력 값과 빈 목록을 포함한 현재 스펙
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SpecProfileResDto getCurrentProfile(Long memberId) {
        log.info("[SpecProfileService] 회원 스펙 조회 시작 | getCurrentProfile() - START | memberId: {}", memberId);
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
        log.info("[SpecProfileService] 회원 스펙 조회 종료 | getCurrentProfile() - END | profileId: {}", profile.getId());
        return result;
    }

    /**
     * 학력별 단일 전공을 일괄 조회하며 복수 연결은 무결성 오류로 처리한다.
     * @param academics 본인 프로필의 학력 목록
     * @return 학력 ID별 전공
     */
    private Map<Long, Major> findAcademicMajors(List<AcademicAbility> academics) {
        log.debug("[SpecProfileService] 전공 조회 시작 | findAcademicMajors() - START");
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
        log.debug("[SpecProfileService] 전공 조회 종료 | findAcademicMajors() - END | count: {}", result.size());
        return result;
    }

    /**
     * 회원 잠금 상태에서 기본 CURRENT를 생성하거나 기존 프로필을 반환한다.
     * @param member 가입한 회원
     * @return 회원의 유일한 CURRENT 프로필
     */
    @Transactional
    public SpecProfile createCurrentProfile(Member member) {
        log.info("[SpecProfileService] createCurrentProfile() - START | memberId: {}", member.getId());
        /* (1) 프로필이 없는 상태에서도 회원 잠금으로 동시 생성을 막는다. */
        Member locked = memberRepository.findByIdForUpdate(member.getId())
                .orElseThrow(() -> new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND));
        List<SpecProfile> profiles = profileRepository.findAllByMemberIdAndType(locked.getId(), SpecProfileType.CURRENT);
        if (profiles.size() > 1) {
            throw new CustomException(SpecProfileErrorCode.INVALID_PROFILE_COUNT);
        }
        /* (2) 기존 CURRENT를 유지하고 없는 회원에게만 기본 프로필을 생성한다. */
        SpecProfile result;
        if (profiles.isEmpty()) {
            creationPolicy.validate(SpecProfileType.CURRENT, 0);
            result = profileRepository.save(mapper.toCurrentProfile(locked));
        } else {
            result = profiles.getFirst();
        }
        log.info("[SpecProfileService] createCurrentProfile() - END | profileId: {}", result.getId());
        return result;
    }

    /**
     * 스펙 임시 저장을 전담 서비스에 위임한다.
     * @param memberId 인증된 회원 ID
     * @param request 저장할 전체 스펙
     */
    public void saveTemporary(Long memberId, SpecProfileTemporarySaveReqDto request) {
        log.info("[SpecProfileService] saveTemporary() - START | memberId: {}", memberId);
        /* (1) 저장 서비스의 트랜잭션에서 검증과 전체 목록 동기화를 수행한다. */
        specProfileSaveService.saveTemporary(memberId, request);
        log.info("[SpecProfileService] saveTemporary() - END | memberId: {}", memberId);
    }
}
