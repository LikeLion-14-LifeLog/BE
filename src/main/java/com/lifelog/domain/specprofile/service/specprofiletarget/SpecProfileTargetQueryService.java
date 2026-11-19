package com.lifelog.domain.specprofile.service.specprofiletarget;

import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetResDto;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.exception.SpecProfileTargetErrorCode;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationResultQueryService;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileTargetQueryService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationResultQueryService resultQueryService;
    private final SpecProfileTargetMapper mapper;
    private final JsonMapper jsonMapper;
    private final SpecProfileRepository profileRepository;
    private final AcademicAbilityRepository academicRepository;
    private final AcademicAbilityMajorRepository academicMajorRepository;
    private final SpecProfileCertificateRepository certificateRepository;
    private final LanguageExamScoreRepository scoreRepository;
    private final ExperienceRepository experienceRepository;

    /**
     * 선택한 목표의 추가 조건만 같은 DB 스냅샷에서 조회한다.
     * @param memberId 인증 회원 ID
     * @return 선택된 목표. 미선택이면 null
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SpecProfileTargetResDto getSelectedTarget(Long memberId) {
        log.info("[SpecProfileTargetQueryService] 선택 목표 조회 | getSelectedTarget() - START | memberId: {}", memberId);
        /* (1) 본인 CURRENT와 선택 TARGET의 소유권·개수·기준 관계를 확인한다. */
        List<SpecProfile> current = profileRepository.findAllByMemberIdAndType(memberId, SpecProfileType.CURRENT);
        if (current.isEmpty()) throw new CustomException(SpecProfileErrorCode.CURRENT_NOT_FOUND);
        if (current.size() != 1) throw new CustomException(SpecProfileErrorCode.INVALID_PROFILE_COUNT);
        List<SpecProfile> selected = profileRepository.findAllByMemberIdAndTypeAndTargetSpecSelectedTrue(memberId, SpecProfileType.TARGET);
        if (selected.isEmpty()) {
            log.info("[SpecProfileTargetQueryService] 선택 목표 조회 | getSelectedTarget() - END | selected: false");
            return null;
        }
        if (selected.size() != 1) throw new CustomException(SpecProfileTargetErrorCode.INVALID_TARGET_STATE);
        SpecProfile target = selected.getFirst();
        if (target.getBaseSpecProfile() == null || !target.getBaseSpecProfile().getId().equals(current.getFirst().getId())) {
            throw new CustomException(SpecProfileTargetErrorCode.INVALID_TARGET_STATE);
        }
        if (!target.isTargetConditionsRecorded()) {
            throw new CustomException(SpecProfileTargetErrorCode.TARGET_CONDITIONS_NOT_RECORDED);
        }
        /* (2) 저장 시 구분한 추가 목표만 조회하며 이름은 기존 EntityGraph로 일괄 로딩한다. */
        List<AcademicAbility> academics = academicRepository.findAllBySpecProfileId(target.getId()).stream()
                .filter(AcademicAbility::isTargetAdditional).sorted(Comparator.comparing(AcademicAbility::getId)).toList();
        Map<Long, Major> majors = findAdditionalMajors(academics);
        List<SpecProfileCertificate> certificates = certificateRepository.findAllBySpecProfileId(target.getId()).stream()
                .filter(SpecProfileCertificate::isTargetAdditional).sorted(Comparator.comparing(SpecProfileCertificate::getId)).toList();
        List<LanguageExamScore> scores = scoreRepository.findAllBySpecProfileId(target.getId()).stream()
                .filter(LanguageExamScore::isTargetAdditional).sorted(Comparator.comparing(LanguageExamScore::getId)).toList();
        List<Experience> experiences = experienceRepository.findAllBySpecProfileId(target.getId()).stream()
                .filter(Experience::isTargetAdditional).sorted(Comparator.comparing(Experience::getId)).toList();
        /* (3) 트랜잭션 안에서 DTO로 변환하며 AI·캐시·저장 로직은 실행하지 않는다. */
        SpecProfileTargetResDto result = mapper.toSelectedTargetResDto(target, academics, majors, certificates, scores, experiences);
        log.info("[SpecProfileTargetQueryService] 선택 목표 조회 | getSelectedTarget() - END | targetId: {}", target.getId());
        return result;
    }

    /** 추가 학력의 전공을 일괄 조회하고 복수 연결은 기존 무결성 오류로 처리한다. */
    private Map<Long, Major> findAdditionalMajors(List<AcademicAbility> academics) {
        log.debug("[SpecProfileTargetQueryService] 추가 전공 조회 | findAdditionalMajors() - START");
        Map<Long, Major> majors = new HashMap<>();
        if (!academics.isEmpty()) {
            for (AcademicAbilityMajor link : academicMajorRepository.findAllByAcademicAbilityIdIn(academics.stream().map(AcademicAbility::getId).toList())) {
                if (majors.putIfAbsent(link.getAcademicAbility().getId(), link.getMajor()) != null) {
                    throw new CustomException(SpecProfileErrorCode.INVALID_MAJOR_COUNT);
                }
            }
        }
        log.debug("[SpecProfileTargetQueryService] 추가 전공 조회 | findAdditionalMajors() - END | count: {}", majors.size());
        return majors;
    }

    /**
     * 비교 기준으로 사용할 현재 스펙의 저장 진단을 확인한다.
     * @param memberId 인증 회원
     * @param base 요청 CURRENT
     * @return 현재 스펙과 일치하는 완료 진단
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileAnnualSalaryAnalyzationResDto requireStoredCurrentAnalysis(Long memberId, SpecProfileAnalyzationSnapshot base) {
        log.info("[SpecProfileTargetQueryService] 기준 연봉 확인 | requireStoredCurrentAnalysis() - START | profileId: {}", base.specProfileId());
        SpecProfileAnnualSalaryAnalyzationResDto result = findStoredCurrentAnalysis(memberId, base);
        if (result == null) throw new CustomException(SpecProfileTargetErrorCode.CURRENT_ANALYSIS_REQUIRED);
        log.info("[SpecProfileTargetQueryService] 기준 연봉 확인 | requireStoredCurrentAnalysis() - END | profileId: {}", base.specProfileId());
        return result;
    }

    /**
     * 현재 스냅샷과 일치하는 최신 완료 진단을 조회하며 없는 결과는 저장하지 않는다.
     * @param memberId 인증 회원
     * @param base 요청 CURRENT
     * @return 기존 진단 결과. 없거나 낡은 진단이면 null
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileAnnualSalaryAnalyzationResDto findStoredCurrentAnalysis(Long memberId, SpecProfileAnalyzationSnapshot base) {
        log.info("[SpecProfileTargetQueryService] 기존 CURRENT 진단 조회 | findStoredCurrentAnalysis() - START | profileId: {}", base.specProfileId());
        SpecProfileAnalyzationRun run = runRepository.findFirstBySpecProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                base.specProfileId(), SpecProfileAnalyzationStatus.COMPLETED).orElse(null);
        SpecProfileAnnualSalaryAnalyzationResDto result = null;
        if (run != null) {
            try {
                SpecProfileAnalyzationSnapshot saved = jsonMapper.readValue(run.getSpecProfileSnapshotJson(), SpecProfileAnalyzationSnapshot.class);
                if (mapper.comparisonKey(base).equals(mapper.comparisonKey(saved))) {
                    result = resultQueryService.getRunResult(memberId, run.getId()).result();
                }
            } catch (JacksonException exception) {
                log.warn("[SpecProfileTargetQueryService] 기존 진단 스냅샷 해석 실패 | runId: {}", run.getId());
            } catch (CustomException exception) {
                if (exception.getErrorCode() != SpecProfileAnalyzationErrorCode.INCOMPLETE_SAVED_RESULT
                        && exception.getErrorCode() != SpecProfileAnalyzationErrorCode.INVALID_SNAPSHOT) throw exception;
                log.warn("[SpecProfileTargetQueryService] 불완전한 기존 진단 제외 | runId: {}", run.getId());
            }
        }
        log.info("[SpecProfileTargetQueryService] 기존 CURRENT 진단 조회 | findStoredCurrentAnalysis() - END | found: {}", result != null);
        return result;
    }
}
