package com.lifelog.domain.specprofile.service.specprofiletarget;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.exception.SpecProfileTargetErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationMapper;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import com.lifelog.domain.specprofile.policy.SpecProfileCreationPolicy;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.service.SpecProfileQueryService;
import com.lifelog.domain.specprofile.service.academicability.AcademicAbilityService;
import com.lifelog.domain.specprofile.service.certification.SpecProfileCertificateService;
import com.lifelog.domain.specprofile.service.experience.ExperienceService;
import com.lifelog.domain.specprofile.service.languageexam.LanguageExamScoreService;
import com.lifelog.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileTargetSaveService {
    private final SpecProfileRepository profileRepository;
    private final SpecProfileQueryService queryService;
    private final SpecProfileAnalyzationMapper snapshotMapper;
    private final SpecProfileTargetMapper mapper;
    private final SpecProfileCreationPolicy creationPolicy;
    private final MajorRepository majorRepository;
    private final CertificationRepository certificationRepository;
    private final LanguageExamRepository examRepository;
    private final AcademicAbilityService academicService;
    private final SpecProfileCertificateService certificateService;
    private final LanguageExamScoreService languageService;
    private final ExperienceService experienceService;
    private final EntityManager entityManager;

    /**
     * AI 처리를 완료한 사용자 목표의 프로필과 하위 스펙만 원자적으로 저장한다.
     * @param memberId 인증 회원
     * @param capturedBase 분석 시점 CURRENT
     * @param changes 사용자 추가 목표
     * @param companyGroupScale 목표 조건의 기업 규모. 미지정이면 null
     * @return 생성 또는 갱신한 1번 TARGET ID
     */
    @Transactional
    public Long upsertUserTarget(Long memberId, SpecProfileAnalyzationSnapshot capturedBase, SpecProfileTargetChanges changes,
            CompanyGroupScale companyGroupScale) {
        log.info("[SpecProfileTargetSaveService] 사용자 목표 저장 | upsertUserTarget() - START | memberId: {}", memberId);
        /* (1) CURRENT 잠금으로 동일 사용자의 목표 생성을 직렬화하고 분석 이후 변경을 검증한다. */
        List<SpecProfile> currentProfiles = profileRepository.findForUpdate(memberId, SpecProfileType.CURRENT);
        if (currentProfiles.isEmpty()) throw new CustomException(SpecProfileErrorCode.CURRENT_NOT_FOUND);
        if (currentProfiles.size() != 1) throw new CustomException(SpecProfileErrorCode.INVALID_PROFILE_COUNT);
        SpecProfile base = currentProfiles.getFirst();
        SpecProfileAnalyzationSnapshot latest = snapshotMapper.toSnapshot(queryService.getCurrentProfile(memberId));
        if (!mapper.comparisonKey(capturedBase).equals(mapper.comparisonKey(latest))) {
            throw new CustomException(SpecProfileTargetErrorCode.BASE_SPEC_CHANGED);
        }
        /* (2) 1번 TARGET만 생성 또는 재사용하며 AI 대안의 프로필은 만들지 않는다. */
        List<SpecProfile> targets = profileRepository.findAllByMemberIdAndType(memberId, SpecProfileType.TARGET);
        List<SpecProfile> userSlots = targets.stream().filter(value -> value.getSortOrder() == 1).toList();
        if (userSlots.size() > 1) throw new CustomException(SpecProfileTargetErrorCode.INVALID_TARGET_STATE);
        SpecProfile target;
        if (userSlots.isEmpty()) {
            creationPolicy.validate(SpecProfileType.TARGET, targets.size());
            target = profileRepository.save(mapper.toUserTarget(base));
        } else {
            target = userSlots.getFirst();
            if (target.getBaseSpecProfile() != null && !target.getBaseSpecProfile().getId().equals(base.getId())) {
                throw new CustomException(SpecProfileTargetErrorCode.INVALID_TARGET_STATE);
            }
            target.applyUserTarget(base);
        }
        /* (3) CURRENT 항목의 PK를 복사하지 않고 사용자 목표의 전체 스펙을 TARGET에 동기화한다. */
        List<AcademicAbilitySaveReqDto> academics = mapper.toAcademicRequests(capturedBase, changes);
        List<CertificateSaveReqDto> certificates = mapper.toCertificateRequests(capturedBase, changes);
        List<LanguageExamScoreSaveReqDto> scores = mapper.toLanguageRequests(capturedBase, changes);
        Map<Long, Major> majors = majorRepository.findAllById(academics.stream().map(AcademicAbilitySaveReqDto::majorId).toList())
                .stream().collect(Collectors.toMap(Major::getId, Function.identity()));
        Map<Long, Certification> certificateReferences = certificationRepository.findAllById(certificates.stream()
                .map(CertificateSaveReqDto::certificateId).toList()).stream().collect(Collectors.toMap(Certification::getId, Function.identity()));
        Map<Long, LanguageExam> exams = examRepository.findAllById(scores.stream().map(LanguageExamScoreSaveReqDto::languageExamId).toList())
                .stream().collect(Collectors.toMap(LanguageExam::getId, Function.identity()));
        List<AcademicAbility> savedAcademics = academicService.syncAcademicAbilities(target, academics, majors);
        List<SpecProfileCertificate> savedCertificates = certificateService.syncCertificates(target, certificates, certificateReferences);
        List<LanguageExamScore> savedScores = languageService.syncLanguageExamScores(target, scores, exams);
        List<Experience> savedExperiences = experienceService.syncExperiences(target, mapper.toExperienceRequests(capturedBase, changes));
        /* (4) 입력 순서에 대응하는 저장 엔티티에 추가 목표 구분을 기록한다. */
        for (int index = 0; index < savedAcademics.size(); index++) {
            savedAcademics.get(index).markTargetAdditional(index >= capturedBase.academicAbilities().size());
        }
        for (int index = 0; index < savedCertificates.size(); index++) {
            savedCertificates.get(index).markTargetAdditional(index >= capturedBase.certificates().size());
        }
        for (int index = 0; index < savedScores.size(); index++) {
            savedScores.get(index).markTargetAdditional(index >= capturedBase.languageExamScores().size());
        }
        for (int index = 0; index < savedExperiences.size(); index++) {
            savedExperiences.get(index).markTargetAdditional(index >= capturedBase.experiences().size());
        }
        target.recordTargetConditions(companyGroupScale);
        for (SpecProfile previous : targets) {
            if (!previous.getId().equals(target.getId())) previous.deselectTarget();
        }
        entityManager.flush();
        Long targetId = target.getId();
        profileRepository.updateModifiedAt(targetId, LocalDateTime.now());
        log.info("[SpecProfileTargetSaveService] 사용자 목표 저장 | upsertUserTarget() - END | targetId: {}", targetId);
        return targetId;
    }
}
