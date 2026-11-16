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
        /*
         * (1) 실행 행을 잠가 단계 완료와 취소·실패·결과 저장이 동시에 상태를 변경하지 않도록 한다.
         */
        SpecProfileAnalyzationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new CustomException(SpecProfileAnalyzationErrorCode.RUN_NOT_FOUND));

        /*
         * (2) 순번 기준으로 전체 이력을 읽고, 지정한 단계의 기존 완료 로그를 찾아 중복 저장을 방지한다.
         */
        List<SpecProfileAnalyzationEventLog> events = eventRepository.findAllByRun_IdOrderBySortOrderAsc(runId);
        SpecProfileAnalyzationEventLog completed = events.stream()
                .filter(event -> event.getStep() == step && event.getStatus() == SpecProfileAnalyzationStatus.COMPLETED)
                .findFirst().orElse(null);

        /*
         * (3) 진행 중이 아닌 실행은 추가 처리를 생략한다.
         * 단, RESULT_SAVE는 결과 저장 트랜잭션에서 이미 실행과 단계가 완료되므로 기존 완료 이력이 있으면 허용한다.
         */
        if (run.getStatus() != SpecProfileAnalyzationStatus.IN_PROGRESS
                && !(step == SpecProfileAnalyzationStep.RESULT_SAVE && run.getStatus() == SpecProfileAnalyzationStatus.COMPLETED && completed != null)) {
            log.info("[SpecProfileAnalyzationProgressService] 종료 실행 생략 | completeStep() - END | runId: {}", runId);
            return Optional.empty();
        }

        /*
         * (4) 완료 이력이 없는 단계만 현재 진행 단계와 일치하는지 검증한다.
         * 이미 완료된 단계의 재호출은 아래 저장을 건너뛰고 기존 이력으로 응답을 구성한다.
         */
        if (completed == null) {
            SpecProfileAnalyzationEventLog active = events.stream().filter(event -> event.getStep() != null)
                    .reduce((first, second) -> second).orElse(null);
            if (active == null || active.getStep() != step || active.getStatus() != SpecProfileAnalyzationStatus.IN_PROGRESS) {
                throw new CustomException(SpecProfileAnalyzationErrorCode.INTERNAL_SERVER_ERROR);
            }

            /*
             * (5) 현재 단계 완료와 order가 바로 다음인 단계의 시작을 같은 트랜잭션에 기록한다.
             * 새로 저장한 로그까지 응답에 반영하도록 전체 이력을 다시 조회한다.
             */
            completed = eventLogService.append(run, step, SpecProfileAnalyzationStatus.COMPLETED);
            SpecProfileAnalyzationStep next = Arrays.stream(SpecProfileAnalyzationStep.values())
                    .filter(candidate -> candidate.getOrder() > step.getOrder())
                    .min(Comparator.comparingInt(SpecProfileAnalyzationStep::getOrder)).orElse(null);
            if (next != null) eventLogService.append(run, next, SpecProfileAnalyzationStatus.IN_PROGRESS);
            events = eventRepository.findAllByRun_IdOrderBySortOrderAsc(runId);
        }

        /*
         * (6) 해당 단계 완료 시점의 이력만 선택해 이후 단계의 상태가 재호출 응답에 섞이지 않도록 한다.
         * 일반 단계는 다음 단계 시작 로그까지, RESULT_SAVE는 단계 완료 로그까지만 포함해 실행 전체 완료 로그를 제외한다.
         */
        int completedOrder = completed.getSortOrder();
        List<SpecProfileAnalyzationEventLog> prefix = events.stream()
                .filter(event -> event.getSortOrder() <= completedOrder + (step == SpecProfileAnalyzationStep.RESULT_SAVE ? 0 : 1)).toList();

        /*
         * (7) progress용 실행 상태는 IN_PROGRESS로 구성해 최종 completed 이벤트와 구분한다.
         * 반환 후 트랜잭션 커밋이 완료되면 호출자인 SSE Registry가 이 응답을 전송한다.
         */
        SpecProfileAnalyzationEventResDto result = mapper.toEventResDto(run, SpecProfileAnalyzationStatus.IN_PROGRESS, prefix);
        log.info("[SpecProfileAnalyzationProgressService] 단계 완료 | completeStep() - END | runId: {}, eventId: {}", runId, result.eventLogId());
        return Optional.of(result);
    }
}
