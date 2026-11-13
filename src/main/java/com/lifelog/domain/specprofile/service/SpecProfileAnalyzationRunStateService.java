package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationRunStateService {
    private final SpecProfileAnalyzationRunRepository runRepository;

    /**
     * QUEUED 실행만 독립 트랜잭션에서 IN_PROGRESS로 전환한다.
     * @param runId 실행 ID
     * @return 이번 호출에서 실행을 시작했는지 여부
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startIfQueued(Long runId) {
        log.info("[SpecProfileAnalyzationRunStateService] 실행 시작 | startIfQueued() - START | runId: {}", runId);
        int updated = runRepository.transitionStatus(runId, SpecProfileAnalyzationStatus.QUEUED,
                SpecProfileAnalyzationStatus.IN_PROGRESS);
        if (updated == 0 && !runRepository.existsById(runId)) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND);
        }
        log.info("[SpecProfileAnalyzationRunStateService] 실행 시작 | startIfQueued() - END | runId: {}, started: {}", runId, updated == 1);
        return updated == 1;
    }

    /**
     * 분석 단계 실패를 독립 트랜잭션에서 저장한다. 시작 단계가 실패한 QUEUED 실행도 포함한다.
     * @param runId 실행 ID
     * @return FAILED로 변경했는지 여부. 이미 종료·취소됐거나 없는 실행은 변경하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failIfRunning(Long runId) {
        log.info("[SpecProfileAnalyzationRunStateService] 실행 실패 저장 | failIfRunning() - START | runId: {}", runId);
        int updated = runRepository.transitionStatuses(runId,
                Set.of(SpecProfileAnalyzationStatus.QUEUED, SpecProfileAnalyzationStatus.IN_PROGRESS),
                SpecProfileAnalyzationStatus.FAILED);
        log.info("[SpecProfileAnalyzationRunStateService] 실행 실패 저장 | failIfRunning() - END | runId: {}, updated: {}", runId, updated);
        return updated == 1;
    }
}
