package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Component
public class SpecProfileAnalyzationMapper {
    /** 저장된 전체 스펙을 분석 전용 스냅샷으로 복사한다. @param profile 저장된 스펙 @return 분석 입력 */
    public SpecProfileAnalyzationSnapshot toSnapshot(SpecProfileResDto profile) {
        return SpecProfileAnalyzationSnapshot.builder().specProfileId(profile.specProfileId())
                .birthYear(profile.birthYear()).residenceRegion(profile.residenceRegion())
                .jobId(profile.jobId()).jobName(profile.desiredJobName())
                .academicAbilities(List.copyOf(profile.academicAbilities()))
                .certificates(List.copyOf(profile.certificates()))
                .languageExamScores(List.copyOf(profile.languageExamScores()))
                .experiences(List.copyOf(profile.experiences())).build();
    }

    /** 실행 이력을 생성한다. @param profile 잠금 프로필 @param job 희망 직무 @param json 스냅샷 @param now 요청 시각 @return 대기 실행 */
    public SpecProfileAnalyzationRun toQueuedRun(SpecProfile profile, Job job, String json, LocalDateTime now) {
        return SpecProfileAnalyzationRun.builder().specProfile(profile).job(job)
                .specProfileSnapshotJson(json).createdAt(now).status(SpecProfileAnalyzationStatus.QUEUED).build();
    }

    /** 최초 이벤트를 생성한다. @param run 저장된 실행 @return 순번 0의 접수 이벤트 */
    public SpecProfileAnalyzationEventLog toQueuedEvent(SpecProfileAnalyzationRun run) {
        return SpecProfileAnalyzationEventLog.builder().run(run).sortOrder(0)
                .status(run.getStatus()).createdAt(run.getCreatedAt()).build();
    }

    /** 접수 응답을 생성한다. @param run 저장된 실행 @return 전체 단계와 구독 경로를 포함한 응답 */
    public SpecProfileAnalyzationRunResDto toRunResDto(SpecProfileAnalyzationRun run) {
        List<SpecProfileAnalyzationStepResDto> steps = Arrays.stream(SpecProfileAnalyzationStep.values())
                .map(this::toStepResDto).toList();
        String resultUrl = "/api/spec-profile/analyzation/runs/" + run.getId();
        return SpecProfileAnalyzationRunResDto.builder().runId(run.getId())
                .specProfileId(run.getSpecProfile().getId()).status(run.getStatus())
                .totalSteps(steps.size()).completedStepCount(0).steps(steps)
                .eventsUrl(resultUrl + "/events").resultUrl(resultUrl)
                .requestedAt(run.getCreatedAt().atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime()).build();
    }

    /** 분석 단계의 화면 표시 정보를 변환한다. @param step 단계 정의 @return 단계 응답 */
    private SpecProfileAnalyzationStepResDto toStepResDto(SpecProfileAnalyzationStep step) {
        return SpecProfileAnalyzationStepResDto.builder().code(step).order(step.getOrder())
                .name(step.getDisplayName()).build();
    }
}
