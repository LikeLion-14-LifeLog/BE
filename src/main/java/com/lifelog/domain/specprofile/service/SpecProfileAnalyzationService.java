package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationMapper;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.domain.specprofile.validation.SpecProfileAnalyzationInputValidator;
import com.lifelog.domain.specprofile.validation.SpecProfileAnalyzationPolicy;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationService {
    private final SpecProfileRepository profileRepository;
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationEventLogRepository eventLogRepository;
    private final JobRepository jobRepository;
    private final SpecProfileQueryService specProfileQueryService;
    private final SpecProfileAnalyzationPolicy policy;
    private final SpecProfileAnalyzationInputValidator inputValidator;
    private final SpecProfileAnalyzationMapper mapper;
    private final JsonMapper jsonMapper;

    /**
     * CURRENT 스펙의 스냅샷과 최초 실행 이벤트를 원자적으로 저장한다.
     * @param memberId JWT로 인증된 회원 ID
     * @return 대기 상태의 분석 접수 응답
     */
    @Transactional
    public SpecProfileAnalyzationRunResDto requestRun(Long memberId) {
        log.info("[SpecProfileAnalyzationService] 연봉 분석 접수 | requestRun() - START | memberId: {}", memberId);
        /* (1) 기존 CURRENT 잠금으로 저장 및 분석 접수를 직렬화한다. */
        List<SpecProfile> profiles = profileRepository.findForUpdate(memberId, SpecProfileType.CURRENT);
        if (profiles.isEmpty()) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.NOT_FOUND);
        }
        if (profiles.size() != 1) {
            throw new CustomException(SpecProfileErrorCode.INVALID_PROFILE_COUNT);
        }
        SpecProfile profile = profiles.getFirst();
        policy.validateLatestRunNotRunning(profile.getId());

        /* (2) 저장된 값을 검증하고 요청 시점의 전체 스펙을 JSON으로 복사한다. */
        SpecProfileResDto savedProfile = specProfileQueryService.getCurrentProfile(memberId);
        inputValidator.validate(savedProfile);
        String snapshot = serializeSnapshot(savedProfile);
        Job job = jobRepository.findById(savedProfile.jobId())
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.INVALID_REQUEST));

        /* (3) 실행과 최초 이벤트를 저장하고 커밋 이후 콜백을 등록한다. */
        SpecProfileAnalyzationRun run = runRepository.save(mapper.toQueuedRun(profile, job, snapshot, LocalDateTime.now()));
        eventLogRepository.saveAndFlush(mapper.toQueuedEvent(run));
        registerAfterCommit(run.getId());
        SpecProfileAnalyzationRunResDto result = mapper.toRunResDto(run);
        log.info("[SpecProfileAnalyzationService] 연봉 분석 접수 | requestRun() - END | runId: {}", run.getId());
        return result;
    }

    /** 저장된 입력을 JSON 스냅샷으로 변환한다. @param profile 저장된 입력 @return 스냅샷 JSON */
    private String serializeSnapshot(SpecProfileResDto profile) {
        log.debug("[SpecProfileAnalyzationService] serializeSnapshot() - START | profileId: {}", profile.specProfileId());
        try {
            String result = jsonMapper.writeValueAsString(mapper.toSnapshot(profile));
            log.debug("[SpecProfileAnalyzationService] serializeSnapshot() - END | profileId: {}", profile.specProfileId());
            return result;
        } catch (JacksonException exception) {
            log.error("[SpecProfileAnalyzationService] 스냅샷 직렬화 실패 | profileId: {}", profile.specProfileId(), exception);
            throw new CustomException(SpecProfileAnalyzationErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** 실행 저장의 커밋 후 호출할 콜백 틀을 등록한다. @param runId 저장된 실행 ID */
    private void registerAfterCommit(Long runId) {
        log.debug("[SpecProfileAnalyzationService] registerAfterCommit() - START | runId: {}", runId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 커밋 이후 비동기 요청의 구현 지점이며 현재는 로그만 기록한다. */
            @Override
            public void afterCommit() {
                log.info("[SpecProfileAnalyzationService] afterCommit() - START | runId: {}", runId);
                // TODO: 스펙 진단 실행자 Queue Class에 연봉 예측 작업을 비동기 요청한다.
                log.info("[SpecProfileAnalyzationService] afterCommit() - END | runId: {}", runId);
            }
        });
        log.debug("[SpecProfileAnalyzationService] registerAfterCommit() - END | runId: {}", runId);
    }
}
