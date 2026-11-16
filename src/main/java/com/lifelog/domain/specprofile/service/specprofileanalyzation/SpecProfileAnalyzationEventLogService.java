package com.lifelog.domain.specprofile.service.specprofileanalyzation;

import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationEventLog;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationEventLogService {
    private final SpecProfileAnalyzationEventLogRepository eventRepository;

    /** 실행 행을 잠근 호출자의 트랜잭션에서 이벤트를 추가한다. @param run 잠금 실행 @param step 단계 또는 실행 전체이면 null @param status 상태 @return 저장 이벤트 */
    @Transactional(propagation = Propagation.MANDATORY)
    public SpecProfileAnalyzationEventLog append(SpecProfileAnalyzationRun run,
            SpecProfileAnalyzationStep step, SpecProfileAnalyzationStatus status) {
        log.debug("[SpecProfileAnalyzationEventLogService] 이벤트 저장 | append() - START | runId: {}, step: {}, status: {}", run.getId(), step, status);
        int order = eventRepository.findFirstByRun_IdOrderBySortOrderDesc(run.getId())
                .map(event -> event.getSortOrder() + 1).orElse(0);
        SpecProfileAnalyzationEventLog result = eventRepository.saveAndFlush(SpecProfileAnalyzationEventLog.builder()
                .run(run).step(step).status(status).sortOrder(order).createdAt(LocalDateTime.now()).build());
        log.debug("[SpecProfileAnalyzationEventLogService] 이벤트 저장 | append() - END | eventId: {}", result.getId());
        return result;
    }

    /** 종료 상태와 해당 시점의 진행 단계를 함께 기록한다. @param run 잠금 종료 실행 @param status 종료 상태 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendTerminal(SpecProfileAnalyzationRun run, SpecProfileAnalyzationStatus status) {
        log.debug("[SpecProfileAnalyzationEventLogService] 종료 이력 | appendTerminal() - START | runId: {}", run.getId());
        List<SpecProfileAnalyzationEventLog> events = eventRepository.findAllByRun_IdOrderBySortOrderAsc(run.getId());
        boolean recorded = events.stream().anyMatch(event -> event.getStep() == null && event.getStatus() == status);
        if (!recorded) {
            SpecProfileAnalyzationEventLog lastStep = events.stream().filter(event -> event.getStep() != null)
                    .reduce((first, second) -> second).orElse(null);
            if (lastStep != null && lastStep.getStatus() == SpecProfileAnalyzationStatus.IN_PROGRESS) {
                append(run, lastStep.getStep(), status);
            }
            append(run, null, status);
        }
        log.debug("[SpecProfileAnalyzationEventLogService] 종료 이력 | appendTerminal() - END | runId: {}", run.getId());
    }
}
