package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.AcademicAbilitySaveReqDto;
import com.lifelog.domain.specprofile.dto.CertificateSaveReqDto;
import com.lifelog.domain.specprofile.dto.LanguageExamScoreSaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.validation.SpecProfileSaveRequestValidator;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.job.service.DesiredJobService;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileSaveService {
    private final SpecProfileRepository profileRepository;
    private final JobRepository jobRepository;
    private final MajorRepository majorRepository;
    private final CertificationRepository certificationRepository;
    private final LanguageExamRepository languageExamRepository;
    
    private final SpecProfileBasicInfoService basicInfoService;
    private final DesiredJobService desiredJobService;
    private final AcademicAbilityService academicAbilityService;
    private final SpecProfileCertificateService certificateService;
    private final LanguageExamScoreService languageExamScoreService;
    private final ExperienceService experienceService;
    private final SpecProfileSaveRequestValidator validator;
    private final EntityManager entityManager;

    /**
     * 본인 CURRENT의 전체 입력 상태를 원자적으로 동기화한다.
     * @param memberId 인증된 회원 ID
     * @param request 저장할 전체 스펙
     */
    @Transactional
    public void saveTemporary(Long memberId, SpecProfileTemporarySaveReqDto request) {
        log.info("[SpecProfileSaveService] 회원 스펙 프로필 임시 저장 시작 | saveTemporary() - START | memberId: {}", memberId);
        /* (1) CURRENT 잠금과 어학 마스터 데이터 존재 여부를 확인한다. */
        List<SpecProfile> profiles = profileRepository.findForUpdate(memberId, SpecProfileType.CURRENT);
        validator.validateCurrentProfileExists(profiles);
        validator.validateSingleCurrentProfile(profiles);
        validator.validateLanguageExamDataInitialized();
        /* (2) 입력과 참조 정보를 검증한다. 초기화 API는 호출하지 않는다. */
        validator.validateDatesAndDuplicateIdsAndConditionalFields(request);

        SpecProfile profile = profiles.getFirst();
        
        Job job = findJob(request.jobId());
        Map<Long, Major> majors = findMajors(request.academicAbilities());
        Map<Long, Certification> certifications = findCertifications(request.certificates());
        Map<Long, LanguageExam> exams = findLanguageExams(request.languageExamScores());
        validator.validateRequestAgainstFetchedReferenceData(request, majors, certifications, exams);

        /* (3) PK는 유지하며 요청에서 제외된 행은 삭제하고 나머지를 덮어쓴다. */
        basicInfoService.updateBasicInfo(profile, request.birthYear(), request.residenceRegion());
        desiredJobService.syncDesiredJob(profile.getMember(), job);
        academicAbilityService.syncAcademicAbilities(profile, request.academicAbilities(), majors);
        certificateService.syncCertificates(profile, request.certificates(), certifications);
        languageExamScoreService.syncLanguageExamScores(profile, request.languageExamScores(), exams);
        experienceService.syncExperiences(profile, request.experiences());
        entityManager.flush();
        profileRepository.updateModifiedAt(profile.getId(), LocalDateTime.now());
        log.info("[SpecProfileSaveService] 회원 스펙 프로필 임시 저장 종료 | saveTemporary() - END | profileId: {}", profile.getId());
    }

    /**
     * 요청한 희망 직무를 조회하고 없는 ID를 거절한다.
     * @param jobId 희망 직무 ID
     * @return 조회된 직무
     */
    private Job findJob(Long jobId) {
        log.debug("[SpecProfileSaveService] findJob() - START | jobId: {}", jobId);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND));
        log.debug("[SpecProfileSaveService] findJob() - END | jobId: {}", jobId);
        return job;
    }

    /**
     * 학력 요청에 포함된 전공을 일괄 조회한다.
     * @param requests 학력 요청 목록
     * @return 전공 ID를 키로 하는 전공 Map
     */
    private Map<Long, Major> findMajors(List<AcademicAbilitySaveReqDto> requests) {
        log.debug("[SpecProfileSaveService] findMajors() - START");
        Map<Long, Major> majors = majorRepository.findAllById(requests.stream()
                .map(AcademicAbilitySaveReqDto::majorId).toList()).stream()
                .collect(Collectors.toMap(Major::getId, Function.identity())); // Function.identity(): 받은 객체를 그대로 반환하는 함수
        log.debug("[SpecProfileSaveService] findMajors() - END | count: {}", majors.size());
        return majors;
    }

    /**
     * 보유 자격증 요청에 포함된 자격증을 일괄 조회한다.
     * @param requests 보유 자격증 요청 목록
     * @return 자격증 ID를 키로 하는 자격증 Map
     */
    private Map<Long, Certification> findCertifications(List<CertificateSaveReqDto> requests) {
        log.debug("[SpecProfileSaveService] findCertifications() - START");
        Map<Long, Certification> certifications = certificationRepository.findAllById(requests.stream()
                .map(CertificateSaveReqDto::certificateId).toList()).stream()
                .collect(Collectors.toMap(Certification::getId, Function.identity()));
        log.debug("[SpecProfileSaveService] findCertifications() - END | count: {}", certifications.size());
        return certifications;
    }

    /**
     * 어학 성적 요청에 포함된 시험을 일괄 조회한다.
     * @param requests 어학 성적 요청 목록
     * @return 시험 ID를 키로 하는 어학 시험 Map
     */
    private Map<Long, LanguageExam> findLanguageExams(List<LanguageExamScoreSaveReqDto> requests) {
        log.debug("[SpecProfileSaveService] findLanguageExams() - START");
        Map<Long, LanguageExam> exams = languageExamRepository.findAllById(requests.stream()
                .map(LanguageExamScoreSaveReqDto::languageExamId).toList()).stream()
                .collect(Collectors.toMap(LanguageExam::getId, Function.identity()));
        log.debug("[SpecProfileSaveService] findLanguageExams() - END | count: {}", exams.size());
        return exams;
    }

}
