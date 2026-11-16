package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationEventResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationProgressStepResDto;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationEventLog;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SpecProfileAnalyzationProgressMapper {
    /** 실행 상태와 해당 시점까지의 로그를 SSE 데이터로 복사한다. @param run 실행 @param status 해당 시점의 실행 상태 @param events 순번 정렬 로그 @return 전체 진행 상태 */
    public SpecProfileAnalyzationEventResDto toEventResDto(SpecProfileAnalyzationRun run,
            SpecProfileAnalyzationStatus status, List<SpecProfileAnalyzationEventLog> events) {
        Map<SpecProfileAnalyzationStep, SpecProfileAnalyzationStatus> states = new EnumMap<>(SpecProfileAnalyzationStep.class);
        for (SpecProfileAnalyzationEventLog event : events) {
            if (event.getStep() != null) states.put(event.getStep(), event.getStatus());
        }
        boolean known = !states.isEmpty() || status == SpecProfileAnalyzationStatus.QUEUED
                || events.stream().anyMatch(event -> event.getStep() == null && event.getStatus() == status
                        && status != SpecProfileAnalyzationStatus.IN_PROGRESS);
        List<SpecProfileAnalyzationProgressStepResDto> steps = Arrays.stream(SpecProfileAnalyzationStep.values())
                .sorted(Comparator.comparingInt(SpecProfileAnalyzationStep::getOrder))
                .map(step -> SpecProfileAnalyzationProgressStepResDto.builder().order(step.getOrder())
                        .code(step.name()).name(step.getDisplayName())
                        .status(known ? states.getOrDefault(step, SpecProfileAnalyzationStatus.QUEUED) : null).build())
                .toList();
        Integer completed = known ? (int) steps.stream()
                .filter(step -> step.status() == SpecProfileAnalyzationStatus.COMPLETED).count() : null;
        Integer current = status == SpecProfileAnalyzationStatus.IN_PROGRESS ? steps.stream()
                .filter(step -> step.status() == SpecProfileAnalyzationStatus.IN_PROGRESS)
                .map(SpecProfileAnalyzationProgressStepResDto::order).findFirst().orElse(null) : null;
        SpecProfileAnalyzationEventLog last = events.isEmpty() ? null : events.getLast();
        LocalDateTime occurredAt = last == null ? (status == SpecProfileAnalyzationStatus.QUEUED ? run.getCreatedAt() : null)
                : last.getCreatedAt();
        if (!known) occurredAt = null;
        return SpecProfileAnalyzationEventResDto.builder().runId(run.getId())
                .eventLogId(last == null ? null : last.getId()).status(status).totalSteps(steps.size())
                .completedStepCount(completed).currentStepOrder(current).steps(steps)
                .resultUrl(status == SpecProfileAnalyzationStatus.COMPLETED
                        ? "/api/spec-profile/analyzation/runs/" + run.getId() : null)
                .occurredAt(occurredAt == null ? null : occurredAt.atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime()).build();
    }
}
