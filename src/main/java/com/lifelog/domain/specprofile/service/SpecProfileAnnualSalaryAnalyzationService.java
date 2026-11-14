package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.service.keep.KeepSalaryMatchedSampleStatisticsService;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryRecordSelectionService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnnualSalaryAnalyzationMapper;
import com.lifelog.domain.specprofile.service.openApi.OpenApiPromptBuilder;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import com.lifelog.global.infra.openai.OpenApiUtil;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileAnnualSalaryAnalyzationService {
    private final SpecProfileAnalyzationRunStateService runStateService;
    private final SpecProfileAnalyzationSnapshotQueryService snapshotQueryService;
    private final SpecProfileSalaryClassificationQueryService classificationQueryService;
    private final SpecProfileSalaryScoreBandService scoreBandService;
    private final KeepSalaryRecordSelectionService recordSelectionService;
    private final KeepSalaryMatchedSampleStatisticsService statisticsService;
    private final OpenApiPromptBuilder promptBuilder;
    private final OpenApiUtil openApiUtil;
    private final AnnualSalaryAnalyzationResponseValidator responseValidator;
    private final SpecProfileAnnualSalaryAnalyzationMapper mapper;
    private final SpecProfileAnnualSalaryResultSaveService resultSaveService;

    /**
     * 큐에서 전달된 실행 ID로 연봉 분석을 수행하고 결과 저장과 완료 처리를 연결한다.
     * @param runId 분석 실행 ID
     * @return 연차별 예측과 AI 진단 문구를 저장한 분석 응답. 중복 실행이거나 저장 전에 종료된 실행이면 null
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileAnnualSalaryAnalyzationResDto analyze(Long runId) {
        log.info("[SpecProfileAnnualSalaryAnalyzationService] 연봉 분석 요청 수신 | analyze() - START | runId: {}", runId);
        try {
            /* (1) 별도 트랜잭션에서 QUEUED 실행을 원자적으로 점유한다. */
            if (!runStateService.startIfQueued(runId)) {
                log.info("[SpecProfileAnnualSalaryAnalyzationService] 중복 또는 종료 실행 생략 | analyze() - END | runId: {}", runId);
                return null;
            }
            /* (2) 상태 변경과 분리된 트랜잭션에서 저장된 요청 스냅샷을 조회한다. */
            SpecProfileAnalyzationSnapshot snapshot = snapshotQueryService.getSnapshot(runId);
            /* (3) 별도 읽기 트랜잭션에서 직무 소분류와 항목별 원 조건값을 구성한다. */
            SpecProfileSalaryClassificationData conditions = classificationQueryService.getClassificationData(snapshot);
            /* (4) 트랜잭션 밖에서 학점과 영어 성적 구간을 계산한다. */
            SpecProfileSalaryClassificationData classified = scoreBandService.classify(conditions);
            /* (5) 누적 스펙 조건을 충족하는 패널별 최적 연도 표본을 선택한다. */
            List<KeepSalaryMatchedSample> samples = recordSelectionService.findBestSamples(classified);
            /* (6) 관측 통계를 계산하고 스냅샷의 상세 스펙을 포함한 프롬프트를 생성한다. */
            KeepSalaryMatchedSampleStatistics statistics = statisticsService.calculate(samples);
            OpenApiPrompt prompt = promptBuilder.buildAnnualSalaryAnalyzationPrompt(snapshot, classified, samples, statistics);
            /* (7) 트랜잭션 밖에서 GPT를 호출하고 연차·금액·필수 설명을 검증한다. */
            AnnualSalaryAnalyzationAiResDto ai = openApiUtil.request(prompt, AnnualSalaryAnalyzationAiResDto.class);
            responseValidator.validate(ai);
            /* (8) 서버의 통계와 스냅샷으로 최종 응답을 구성한다. */
            Long firstSalary = ai.salaryResults().stream().filter(prediction -> prediction.annual() == 0)
                    .findFirst().orElseThrow().annualIncomeExpected();
            BigDecimal topPercent = statisticsService.calculateTopPercent(BigDecimal.valueOf(firstSalary), samples);
            SpecProfileAnnualSalaryAnalyzationResDto result = mapper.toResDto(ai, snapshot, classified, samples, statistics, topPercent);
            /* (9) 별도 트랜잭션에서 결과 저장과 COMPLETED 전환을 함께 커밋한다. */
            if (!resultSaveService.saveAndComplete(runId, result)) {
                log.info("[SpecProfileAnnualSalaryAnalyzationService] 저장 전 종료된 실행 생략 | analyze() - END | runId: {}", runId);
                return null;
            }
            log.info("[SpecProfileAnnualSalaryAnalyzationService] 연봉 분석 | analyze() - END | runId: {}, count: {}", runId, samples.size());
            return result;
        } catch (RuntimeException exception) {
            recordFailure(runId, exception);
            throw exception;
        }
    }

    /** 최초 단계부터 발생한 예외를 FAILED로 보상하고, 보상 실패가 원래 예외를 가리지 않게 한다. */
    private void recordFailure(Long runId, RuntimeException original) {
        log.debug("[SpecProfileAnnualSalaryAnalyzationService] 실패 상태 보상 | recordFailure() - START | runId: {}", runId);
        try {
            runStateService.failIfRunning(runId);
        } catch (RuntimeException failure) {
            if (failure != original) original.addSuppressed(failure);
            log.error("[SpecProfileAnnualSalaryAnalyzationService] 실패 상태 저장 실패 | runId: {}, type: {}",
                    runId, failure.getClass().getSimpleName());
        }
        log.debug("[SpecProfileAnnualSalaryAnalyzationService] 실패 상태 보상 | recordFailure() - END | runId: {}", runId);
    }
}
