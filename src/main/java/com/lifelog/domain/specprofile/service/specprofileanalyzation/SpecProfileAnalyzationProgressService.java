package com.lifelog.domain.specprofile.service.specprofileanalyzation;

import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationEventResDto;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationEventLog;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationProgressMapper;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationEventLogRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnalyzationProgressService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationEventLogRepository eventRepository;
    private final SpecProfileAnalyzationEventLogService eventLogService;
    private final SpecProfileAnalyzationProgressMapper mapper;

    /** 지정한 단계 완료와 다음 단계 시작을 독립 커밋한다. 마지막 단계는 결과 저장 시 커밋된 이력을 사용한다. @param runId 실행 ID @param step 완료한 단계 @return 커밋 후 전송할 진행 상태 또는 종료되어 처리하지 않은 경우 빈 값 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SpecProfileAnalyzationEventResDto> completeStep(Long runId, SpecProfileAnalyzationStep step) {
        log.info("[SpecProfileAnalyzationProgressService] 단계 완료 | completeStep() - START | runId: {}, step: {}", runId, step);
        SpecProfileAnalyzationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));
        List<SpecProfileAnalyzationEventLog> events = eventRepository.findAllByRun_IdOrderBySortOrderAsc(runId);
        SpecProfileAnalyzationEventLog completed = events.stream()
                .filter(event -> event.getStep() == step && event.getStatus() == SpecProfileAnalyzationStatus.COMPLETED)
                .findFirst().orElse(null);
        if (run.getStatus() != SpecProfileAnalyzationStatus.IN_PROGRESS
                && !(step == SpecProfileAnalyzationStep.RESULT_SAVE && run.getStatus() == SpecProfileAnalyzationStatus.COMPLETED && completed != null)) {
            log.info("[SpecProfileAnalyzationProgressService] 종료 실행 생략 | completeStep() - END | runId: {}", runId);
            return Optional.empty();
        }
        if (completed == null) {
            SpecProfileAnalyzationEventLog active = events.stream().filter(event -> event.getStep() != null)
                    .reduce((first, second) -> second).orElse(null);
            if (active == null || active.getStep() != step || active.getStatus() != SpecProfileAnalyzationStatus.IN_PROGRESS) {
                throw new CustomException(SpecProfileAnalyzationErrorCode.INTERNAL_SERVER_ERROR);
            }
            completed = eventLogService.append(run, step, SpecProfileAnalyzationStatus.COMPLETED);
            SpecProfileAnalyzationStep next = Arrays.stream(SpecProfileAnalyzationStep.values())
                    .filter(candidate -> candidate.getOrder() > step.getOrder())
                    .min(Comparator.comparingInt(SpecProfileAnalyzationStep::getOrder)).orElse(null);
            if (next != null) eventLogService.append(run, next, SpecProfileAnalyzationStatus.IN_PROGRESS);
            events = eventRepository.findAllByRun_IdOrderBySortOrderAsc(runId);
        }
        int completedOrder = completed.getSortOrder();
        List<SpecProfileAnalyzationEventLog> prefix = events.stream()
                .filter(event -> event.getSortOrder() <= completedOrder + (step == SpecProfileAnalyzationStep.RESULT_SAVE ? 0 : 1)).toList();
        SpecProfileAnalyzationEventResDto result = mapper.toEventResDto(run, SpecProfileAnalyzationStatus.IN_PROGRESS, prefix);
        log.info("[SpecProfileAnalyzationProgressService] 단계 완료 | completeStep() - END | runId: {}, eventId: {}", runId, result.eventLogId());
        return Optional.of(result);
    }
}
