package com.lifelog.domain.specprofile.service.specprofilesimulation;

import com.lifelog.domain.specprofile.dto.AnnualSalaryAnalyzationAiResDto;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAiResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAnalysis;
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
        /* (1) 기존 규칙으로 분류 조건과 비교 표본을 선택한다. */
        SpecProfileSalaryClassificationData classified = scoreBandService.classify(
                classificationQueryService.getClassificationData(snapshot));
        List<KeepSalaryMatchedSample> samples = recordSelectionService.findBestSamples(classified);
        KeepSalaryMatchedSampleStatistics statistics = statisticsService.calculate(samples);
        /* (2) 기업 규모는 전용 프롬프트에 전달하고 기존 응답 계약으로 예측값을 검증한다. */
        OpenApiPrompt prompt = promptBuilder.buildAnnualSalarySimulationPrompt(
                snapshot, classified, samples, statistics, companyGroupScale, baseSnapshot);
        SpecProfileSimulationAiResDto simulationAi = openApiUtil.request(prompt, SpecProfileSimulationAiResDto.class);
        responseValidator.validateSimulationDetails(simulationAi);
        AnnualSalaryAnalyzationAiResDto ai = simulationMapper.toAnalyzationAiResDto(simulationAi);
        responseValidator.validate(ai);
        /* (3) 상위 비율은 AI 대신 서버가 실제 비교 표본으로 계산한다. */
        Long firstSalary = ai.salaryResults().stream().filter(prediction -> prediction.annual() == 0)
                .findFirst().orElseThrow().annualIncomeExpected();
        BigDecimal topPercent = statisticsService.calculateTopPercent(BigDecimal.valueOf(firstSalary), samples);
        SpecProfileAnnualSalaryAnalyzationResDto analysis = analysisMapper.toResDto(ai, snapshot, classified, samples, statistics, topPercent);
        SpecProfileSimulationAnalysis result = simulationMapper.toAnalysis(analysis, simulationAi);
        log.info("[SpecProfileSimulationAnalyzeService] 스냅샷 분석 | analyze() - END | profileId: {}", snapshot.specProfileId());
        return result;
    }
}
