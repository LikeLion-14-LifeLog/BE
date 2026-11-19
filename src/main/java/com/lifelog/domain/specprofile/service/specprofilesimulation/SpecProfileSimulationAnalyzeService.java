package com.lifelog.domain.specprofile.service.specprofilesimulation;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAiResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAnalysis;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationPreparedAnalysis;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnnualSalaryAnalyzationMapper;
import com.lifelog.domain.specprofile.mapper.SpecProfileSimulationMapper;
import com.lifelog.domain.specprofile.service.SpecProfileSalaryClassificationQueryService;
import com.lifelog.domain.specprofile.service.SpecProfileSalaryScoreBandService;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryMatchedSampleStatisticsService;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryRecordSelectionService;
import com.lifelog.domain.specprofile.service.openApi.OpenApiPromptBuilder;
import com.lifelog.domain.specprofile.validation.AnnualSalaryAnalyzationResponseValidator;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import com.lifelog.global.infra.openai.OpenApiUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileSimulationAnalyzeService {
    private final SpecProfileSalaryClassificationQueryService classificationQueryService;
    private final SpecProfileSalaryScoreBandService scoreBandService;
    private final KeepSalaryRecordSelectionService recordSelectionService;
    private final KeepSalaryMatchedSampleStatisticsService statisticsService;
    private final OpenApiPromptBuilder promptBuilder;
    private final OpenApiUtil openApiUtil;
    private final AnnualSalaryAnalyzationResponseValidator responseValidator;
    private final SpecProfileAnnualSalaryAnalyzationMapper analysisMapper;
    private final SpecProfileSimulationMapper simulationMapper;

    /**
     * 기존 분류·표본·통계·검증을 재사용하고 기업 규모를 포함한 전용 프롬프트로 예측한다.
     * @param snapshot 현재 또는 목표 스냅샷
     * @param companyGroupScale 두 시나리오에 동일하게 적용할 기업 규모
     * @param baseSnapshot 추가 목표를 구분하기 위한 현재 스펙
     * @return 저장되지 않은 분석 결과와 목표 준비 정보
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileSimulationAnalysis analyze(SpecProfileAnalyzationSnapshot snapshot,
            CompanyGroupScale companyGroupScale, SpecProfileAnalyzationSnapshot baseSnapshot) {
        log.info("[SpecProfileSimulationAnalyzeService] 스냅샷 분석 | analyze() - START | profileId: {}", snapshot.specProfileId());
        /* (1) 서버 계산과 AI 응답 구성을 분리하여 단일·일괄 예측에서 같은 검증을 사용한다. */
        SpecProfileSimulationPreparedAnalysis prepared = prepareAnalysis(snapshot, companyGroupScale, baseSnapshot);
        SpecProfileSimulationAiResDto simulationAi = openApiUtil.request(prepared.prompt(), SpecProfileSimulationAiResDto.class);
        SpecProfileSimulationAnalysis result = completeAnalysis(prepared, simulationAi);
        log.info("[SpecProfileSimulationAnalyzeService] 스냅샷 분석 | analyze() - END | profileId: {}", snapshot.specProfileId());
        return result;
    }

    /**
     * AI를 호출하지 않고 비교 표본·통계와 동일한 시뮬레이션 입력을 준비한다.
     * @param snapshot 분석할 현재 또는 목표 스냅샷
     * @param companyGroupScale 공통 기업 규모
     * @param baseSnapshot 추가 목표를 구분할 현재 스펙
     * @return 서버 계산 결과와 예측 프롬프트
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileSimulationPreparedAnalysis prepareAnalysis(SpecProfileAnalyzationSnapshot snapshot,
            CompanyGroupScale companyGroupScale, SpecProfileAnalyzationSnapshot baseSnapshot) {
        log.info("[SpecProfileSimulationAnalyzeService] 분석 입력 준비 | prepareAnalysis() - START | profileId: {}", snapshot.specProfileId());
        /* (1) 기존 규칙으로 분류 조건과 비교 표본을 선택한다. */
        SpecProfileSalaryClassificationData classified = scoreBandService.classify(
                classificationQueryService.getClassificationData(snapshot));
        List<KeepSalaryMatchedSample> samples = recordSelectionService.findBestSamples(classified);
        KeepSalaryMatchedSampleStatistics statistics = statisticsService.calculate(samples);
        /* (2) 기업 규모와 기준 스펙을 예측 입력에 반영하며 외부 AI는 호출하지 않는다. */
        OpenApiPrompt prompt = promptBuilder.buildAnnualSalarySimulationPrompt(
                snapshot, classified, samples, statistics, companyGroupScale, baseSnapshot);
        SpecProfileSimulationPreparedAnalysis result = new SpecProfileSimulationPreparedAnalysis(
                snapshot, classified, samples, statistics, prompt);
        log.info("[SpecProfileSimulationAnalyzeService] 분석 입력 준비 | prepareAnalysis() - END | samples: {}", samples.size());
        return result;
    }

    /**
     * 단일·일괄 AI 응답을 같은 규칙으로 검증하고 실제 비교 표본의 통계를 결합한다.
     * @param prepared 서버에서 확정한 예측 입력
     * @param simulationAi 해당 시나리오의 AI 예측
     * @return 검증된 분석 결과와 목표 준비 정보
     */
    public SpecProfileSimulationAnalysis completeAnalysis(SpecProfileSimulationPreparedAnalysis prepared,
            SpecProfileSimulationAiResDto simulationAi) {
        log.info("[SpecProfileSimulationAnalyzeService] 예측 응답 구성 | completeAnalysis() - START | profileId: {}", prepared.snapshot().specProfileId());
        /* (1) 준비 정보와 0·3·5년 차의 연봉 범위를 기존 규칙으로 검증한다. */
        responseValidator.validateSimulationDetails(simulationAi);
        AnnualSalaryAnalyzationAiResDto ai = simulationMapper.toAnalyzationAiResDto(simulationAi);
        responseValidator.validate(ai);
        /* (2) 상위 비율과 관측 통계는 AI 응답이 아닌 서버 계산값을 사용한다. */
        Long firstSalary = ai.salaryResults().stream().filter(prediction -> prediction.annual() == 0)
                .findFirst().orElseThrow().annualIncomeExpected();
        BigDecimal topPercent = statisticsService.calculateTopPercent(BigDecimal.valueOf(firstSalary), prepared.samples());
        SpecProfileAnnualSalaryAnalyzationResDto analysis = analysisMapper.toResDto(ai, prepared.snapshot(),
                prepared.classified(), prepared.samples(), prepared.statistics(), topPercent);
        SpecProfileSimulationAnalysis result = simulationMapper.toAnalysis(analysis, simulationAi);
        log.info("[SpecProfileSimulationAnalyzeService] 예측 응답 구성 | completeAnalysis() - END | profileId: {}", prepared.snapshot().specProfileId());
        return result;
    }
}
