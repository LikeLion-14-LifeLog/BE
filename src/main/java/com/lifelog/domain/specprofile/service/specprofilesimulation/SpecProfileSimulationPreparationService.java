package com.lifelog.domain.specprofile.service.specprofilesimulation;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationContext;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationReqDto;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationMapper;
import com.lifelog.domain.specprofile.mapper.SpecProfileSimulationMapper;
import com.lifelog.domain.specprofile.service.SpecProfileQueryService;
import com.lifelog.domain.specprofile.validation.SpecProfileAnalyzationInputValidator;
import com.lifelog.domain.specprofile.validation.SpecProfileSimulationInputValidator;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileSimulationPreparationService {
    private final SpecProfileQueryService queryService;
    private final SpecProfileAnalyzationInputValidator inputValidator;
    private final SpecProfileSimulationInputValidator simulationValidator;
    private final SpecProfileAnalyzationMapper snapshotMapper;
    private final SpecProfileSimulationMapper simulationMapper;
    private final MajorRepository majorRepository;
    private final CertificationRepository certificationRepository;
    private final LanguageExamRepository languageExamRepository;

    /**
     * 같은 읽기 트랜잭션에서 본인 현재 스펙과 목표 참조 데이터를 읽어 독립 스냅샷을 준비한다.
     * @param memberId 인증 회원 ID
     * @param request 일회성 추가 조건
     * @return 현재·목표 스냅샷. 추가 조건이 없으면 목표는 null
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SpecProfileSimulationContext prepare(Long memberId, SpecProfileSimulationReqDto request) {
        log.info("[SpecProfileSimulationPreparationService] 입력 준비 | prepare() - START | memberId: {}", memberId);
        /* (1) 본인 CURRENT만 조회하고 요청한 기준 프로필과 일치하는지 검증한다. */
        SpecProfileResDto profile = queryService.getCurrentProfile(memberId);
        if (!profile.specProfileId().equals(request.baseSpecProfileId())) {
            throw new CustomException(SpecProfileErrorCode.CURRENT_NOT_FOUND);
        }
        inputValidator.validate(profile);
        SpecProfileAnalyzationSnapshot base = snapshotMapper.toSnapshot(profile);
        SpecProfileSimulationReqDto.Changes changes = request.changes();
        if (changes == null || !changes.hasChanges()) {
            log.info("[SpecProfileSimulationPreparationService] 현재 입력 준비 | prepare() - END | profileId: {}", base.specProfileId());
            return new SpecProfileSimulationContext(base, null);
        }
        /* (2) 목표 조건의 참조 데이터만 일괄 조회하고 검증한다. */
        Map<Long, Major> majors = majorRepository.findAllById(changes.academicAbilities().stream()
                .map(SpecProfileSimulationReqDto.AcademicAbilityChange::majorId).toList()).stream()
                .collect(Collectors.toMap(Major::getId, Function.identity()));
        Map<Long, Certification> certificates = certificationRepository.findAllById(changes.certificates().stream()
                .map(SpecProfileSimulationReqDto.CertificateChange::certificateId).toList()).stream()
                .collect(Collectors.toMap(Certification::getId, Function.identity()));
        Map<Long, LanguageExam> exams = languageExamRepository.findAllById(changes.languageExamScores().stream()
                .map(SpecProfileSimulationReqDto.LanguageExamScoreChange::languageExamId).toList()).stream()
                .collect(Collectors.toMap(LanguageExam::getId, Function.identity()));
        simulationValidator.validate(profile, changes, majors, certificates, exams);
        /* (3) 엔티티 수정 없이 현재 스펙에 목표 항목을 추가한 새 스냅샷을 만든다. */
        SpecProfileAnalyzationSnapshot simulated = simulationMapper.toSimulatedSnapshot(base, changes, majors, certificates, exams);
        log.info("[SpecProfileSimulationPreparationService] 입력 준비 | prepare() - END | profileId: {}", base.specProfileId());
        return new SpecProfileSimulationContext(base, simulated);
    }
}
