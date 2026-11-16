package com.lifelog.domain.specprofile.service.specprofileanalyzation;

import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunStopResDto;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationMapper;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationRunStateService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationEventLogService eventLogService;
    private final SpecProfileAnalyzationMapper mapper;

    /**
     * 본인 실행의 중단 상태와 단계·실행 취소 로그를 함께 커밋한다.
     * @param memberId 인증 회원 ID
     * @param runId 중단할 실행 ID
     * @return 최초 중단 시각을 포함한 중단 응답
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SpecProfileAnalyzationRunStopResDto stopOwnedRun(Long memberId, Long runId) {
        log.info("[SpecProfileAnalyzationRunStateService] 실행 중단 | stopOwnedRun() - START | memberId: {}, runId: {}", memberId, runId);
        /* (1) 유효한 경로 변수와 소유권을 검증하고 실행 행만 잠근다. */
        if (runId == null || runId <= 0) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_REQUEST);
        }
        SpecProfileAnalyzationRun run = runRepository.findOwnedRunForUpdate(runId, memberId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        /* (2) DB 정밀도에 맞춘 최초 중단 시각과 취소 이력을 같은 트랜잭션에 저장한다. */
        run.cancelAnnualSalaryAnalyzation(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        eventLogService.appendTerminal(run, SpecProfileAnalyzationStatus.CANCELED);
        SpecProfileAnalyzationRunStopResDto result = mapper.toRunStopResDto(run);
        log.info("[SpecProfileAnalyzationRunStateService] 실행 중단 | stopOwnedRun() - END | runId: {}, canceledAt: {}", runId, result.canceledAt());
        return result;
    }

    /**
     * QUEUED 실행만 독립 트랜잭션에서 IN_PROGRESS로 전환한다.
     * @param runId 실행 ID
     * @return 이번 호출에서 실행을 시작했는지 여부
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startIfQueued(Long runId) {
        log.info("[SpecProfileAnalyzationRunStateService] 실행 시작 | startIfQueued() - START | runId: {}", runId);
        SpecProfileAnalyzationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        boolean started = run.getStatus() == SpecProfileAnalyzationStatus.QUEUED;
        if (started) {
            run.startAnnualSalaryAnalyzation();
            eventLogService.append(run, null, SpecProfileAnalyzationStatus.IN_PROGRESS);
            eventLogService.append(run, SpecProfileAnalyzationStep.RUN_START, SpecProfileAnalyzationStatus.IN_PROGRESS);
        }
        log.info("[SpecProfileAnalyzationRunStateService] 실행 시작 | startIfQueued() - END | runId: {}, started: {}", runId, started);
        return started;
    }

    /**
     * 분석 단계 실패를 독립 트랜잭션에서 저장한다. 시작 단계가 실패한 QUEUED 실행도 포함한다.
     * @param runId 실행 ID
     * @return FAILED로 변경했는지 여부. 이미 종료·취소됐거나 없는 실행은 변경하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failIfRunning(Long runId) {
        log.info("[SpecProfileAnalyzationRunStateService] 실행 실패 저장 | failIfRunning() - START | runId: {}", runId);
        Optional<SpecProfileAnalyzationRun> found = runRepository.findByIdForUpdate(runId);
        boolean changed = found.isPresent() && found.get().getStatus().isRunning();
        if (changed) {
            SpecProfileAnalyzationRun run = found.get();
            run.failAnnualSalaryAnalyzation();
            eventLogService.appendTerminal(run, SpecProfileAnalyzationStatus.FAILED);
        }
        log.info("[SpecProfileAnalyzationRunStateService] 실행 실패 저장 | failIfRunning() - END | runId: {}, changed: {}", runId, changed);
        return changed;
    }

    /** 확정된 취소 상태를 관측한 뒤 단계 및 실행의 취소 이력을 보완한다. @param runId 실행 ID */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCanceled(Long runId) {
        log.info("[SpecProfileAnalyzationRunStateService] 취소 이력 저장 | recordCanceled() - START | runId: {}", runId);
        runRepository.findByIdForUpdate(runId).filter(run -> run.getStatus() == SpecProfileAnalyzationStatus.CANCELED)
                .ifPresent(run -> eventLogService.appendTerminal(run, SpecProfileAnalyzationStatus.CANCELED));
        log.info("[SpecProfileAnalyzationRunStateService] 취소 이력 저장 | recordCanceled() - END | runId: {}", runId);
    }
}
