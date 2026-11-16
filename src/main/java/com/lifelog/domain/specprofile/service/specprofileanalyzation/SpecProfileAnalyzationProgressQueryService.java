package com.lifelog.domain.specprofile.service.specprofileanalyzation;

import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationEventResDto;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationProgressMapper;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationEventLogRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationProgressQueryService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationEventLogRepository eventRepository;
    private final SpecProfileAnalyzationProgressMapper mapper;

    /** 본인 실행과 재접속 커서를 검증하고 일관된 최신 스냅샷을 조회한다. @param memberId 인증 회원 @param runId 실행 ID @param lastEventId 마지막 이벤트 PK 또는 null @return 전체 진행 상태 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public SpecProfileAnalyzationEventResDto getOwnedSnapshot(Long memberId, Long runId, String lastEventId) {
        log.info("[SpecProfileAnalyzationProgressQueryService] 구독 상태 조회 | getOwnedSnapshot() - START | memberId: {}, runId: {}", memberId, runId);
        if (runId == null || runId <= 0) throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_REQUEST);
        SpecProfileAnalyzationRun run = runRepository.findOwnedRun(runId, memberId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        validateCursor(runId, lastEventId);
        SpecProfileAnalyzationEventResDto result = mapper.toEventResDto(run, run.getStatus(), eventRepository.findAllByRun_IdOrderBySortOrderAsc(runId));
        log.info("[SpecProfileAnalyzationProgressQueryService] 구독 상태 조회 | getOwnedSnapshot() - END | runId: {}, eventId: {}", runId, result.eventLogId());
        return result;
    }

    /** 분석 처리 내부에서 커밋된 실행 진행 상태를 조회한다. @param runId 분석 실행 ID @return 전체 진행 상태 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public SpecProfileAnalyzationEventResDto getSnapshot(Long runId) {
        log.debug("[SpecProfileAnalyzationProgressQueryService] 상태 조회 | getSnapshot() - START | runId: {}", runId);
        SpecProfileAnalyzationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        SpecProfileAnalyzationEventResDto result = mapper.toEventResDto(run, run.getStatus(), eventRepository.findAllByRun_IdOrderBySortOrderAsc(runId));
        log.debug("[SpecProfileAnalyzationProgressQueryService] 상태 조회 | getSnapshot() - END | runId: {}", runId);
        return result;
    }

    /** 커서가 양의 이벤트 PK이며 동일 실행에 속하는지 확인한다. @param runId 실행 ID @param value 커서 */
    private void validateCursor(Long runId, String value) {
        log.debug("[SpecProfileAnalyzationProgressQueryService] 커서 검증 | validateCursor() - START | runId: {}", runId);
        if (value != null) {
            try {
                long id = Long.parseLong(value);
                if (id <= 0 || !eventRepository.existsByRun_IdAndId(runId, id)) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_REQUEST);
            }
        }
        log.debug("[SpecProfileAnalyzationProgressQueryService] 커서 검증 | validateCursor() - END | runId: {}", runId);
    }
}
