package com.lifelog.domain.specprofile.service.specprofiletarget;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.exception.SpecProfileTargetErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import com.lifelog.domain.specprofile.policy.SpecProfileTargetCachePolicy;
import com.lifelog.domain.specprofile.service.openApi.OpenApiPromptBuilder;
import com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationAnalyzeService;
import com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationCacheRegistry;
import com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationPreparationService;
import com.lifelog.domain.specprofile.validation.SpecProfileTargetRecommendationValidator;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import com.lifelog.global.infra.openai.OpenApiUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileTargetService {
    private final SpecProfileSimulationPreparationService preparationService;
    private final SpecProfileSimulationAnalyzeService analyzeService;
    private final SpecProfileSimulationCacheRegistry cacheRegistry;
    private final SpecProfileTargetCachePolicy cachePolicy;
    private final SpecProfileTargetRecommendationValidator recommendationValidator;
    private final SpecProfileTargetReferenceQueryService referenceQueryService;
    private final SpecProfileTargetQueryService queryService;
    private final SpecProfileTargetSaveService saveService;
    private final SpecProfileTargetMapper mapper;
    private final OpenApiPromptBuilder promptBuilder;
    private final OpenApiUtil openApiUtil;

    /**
     * 사용자 목표만 저장하고 AI 두 번 호출로 세 선택지의 연봉·준비 정보를 반환한다.
     * @param memberId 인증 회원 ID
     * @param request 기준 CURRENT와 사용자 추가 목표
     * @return 사용자 슬롯 1과 저장하지 않은 AI 대안 슬롯 2·3
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileTargetSaveResDto saveTarget(Long memberId, SpecProfileTargetSaveReqDto request) {
        log.info("[SpecProfileTargetService] 목표 저장 및 대안 비교 | saveTarget() - START | memberId: {}", memberId);
        /* (1) 사용자 조건과 캐시를 확인하고 AI 호출 없이 필요한 비교 표본·통계를 준비한다. */
        recommendationValidator.validateDates(request.changes());
        SpecProfileSimulationContext prepared = preparationService.prepare(memberId, mapper.toSimulationRequest(request));
        UserSimulation cached = findUserSimulation(memberId, prepared, request.companyGroupScale());
        SpecProfileAnalyzationSnapshot target = prepared.simulated() == null ? prepared.base() : prepared.simulated();
        SpecProfileTargetScenarioDto user = SpecProfileTargetScenarioDto.builder()
                .title(mapper.toUserTitle(prepared.base(), target, request.changes())).changes(request.changes())
                .snapshot(target).analysis(cached == null ? null : cached.user()).build();
        Map<Integer, SpecProfileSimulationPreparedAnalysis> analysisInputs = new LinkedHashMap<>();
        if (cached == null) {
            analysisInputs.put(0, analyzeService.prepareAnalysis(prepared.base(), request.companyGroupScale(), prepared.base()));
            if (prepared.simulated() != null) {
                analysisInputs.put(1, analyzeService.prepareAnalysis(target, request.companyGroupScale(), prepared.base()));
            }
        }
        /* (2) 첫 AI 요청으로 서로 다른 대안을 생성하고 서버에서 스펙과 비교 표본을 검증한다. */
        SpecProfileAnnualSalaryAnalyzationResDto stored = queryService.findStoredCurrentAnalysis(memberId, prepared.base());
        SpecProfileTargetReferenceResDto references = referenceQueryService.findAllowedReferences(prepared.base(), target);
        SpecProfileAnnualSalaryAnalyzationResDto cachedBase = cached == null ? null : cached.base();
        List<SpecProfileTargetScenarioDto> alternatives = generateAlternatives(prepared.base(), cachedBase, stored,
                user, request.companyGroupScale(), references, analysisInputs);
        /* (3) 두 번째 AI 요청으로 캐시에 없는 예측과 대안 종합 평가를 함께 받아 기존 규칙으로 검증한다. */
        OpenApiPrompt prompt = promptBuilder.buildTargetSpecAnalysisPrompt(prepared.base(), cachedBase, user,
                alternatives, request.companyGroupScale(), analysisInputs);
        SpecProfileTargetAnalysisAiResDto batch = openApiUtil.request(prompt, SpecProfileTargetAnalysisAiResDto.class);
        recommendationValidator.validateAnalyses(batch, analysisInputs.keySet());
        Map<Integer, SpecProfileSimulationAnalysis> analyses = new LinkedHashMap<>();
        if (cached != null) {
            analyses.put(0, new SpecProfileSimulationAnalysis(cached.base(), null, null, null));
            analyses.put(1, cached.user());
        }
        for (SpecProfileTargetAnalysisAiResDto.Prediction prediction : batch.analyses()) {
            analyses.put(prediction.slotNumber(), analyzeService.completeAnalysis(
                    analysisInputs.get(prediction.slotNumber()), prediction.analysis()));
        }
        recommendationValidator.validateEvaluation(batch.evaluation());
        SpecProfileTargetScenarioDto analyzedUser = mapper.toAnalyzedScenario(user, analyses.getOrDefault(1, analyses.get(0)));
        List<SpecProfileTargetScenarioDto> analyzedAlternatives = List.of(
                mapper.toAnalyzedScenario(alternatives.get(0), analyses.get(2)),
                mapper.toAnalyzedScenario(alternatives.get(1), analyses.get(3)));
        /* (4) 모든 검증 후 사용자 TARGET만 짧은 트랜잭션에서 저장한다. 검증 실패는 재생성하지 않는다. */
        Long targetId = saveService.upsertUserTarget(memberId, prepared.base(), request.changes(), request.companyGroupScale());
        SpecProfileTargetSaveResDto response = mapper.toResponse(request, targetId, analyses.get(0).analysis(),
                analyzedUser, analyzedAlternatives);
        log.info("[SpecProfileTargetService] 목표 저장 및 대안 비교 | saveTarget() - END | targetId: {}", targetId);
        return response;
    }

    /**
     * 동일 조건의 완전한 캐시만 조회하며 없거나 불일치하면 AI 호출 없이 null을 반환한다.
     * @param memberId 인증 회원 ID
     * @param prepared 현재·사용자 목표 스냅샷
     * @param scale 공통 기업 규모
     * @return 재사용 가능한 현재·목표 분석. 없으면 null
     */
    private UserSimulation findUserSimulation(Long memberId, SpecProfileSimulationContext prepared, CompanyGroupScale scale) {
        log.debug("[SpecProfileTargetService] 사용자 목표 캐시 조회 | findUserSimulation() - START | memberId: {}", memberId);
        SpecProfileSimulationContext cached = null;
        try {
            cached = cacheRegistry.find(memberId).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("[SpecProfileTargetService] 최근 캐시 조회 실패, 일괄 분석 진행 | memberId: {}", memberId, exception);
        }
        UserSimulation result = null;
        if (cachePolicy.canReuse(prepared, cached, scale)) {
            result = new UserSimulation(cached.baseAnalysis(), mapper.toCachedUserAnalysis(cached));
        }
        log.debug("[SpecProfileTargetService] 사용자 목표 캐시 조회 | findUserSimulation() - END | found: {}", result != null);
        return result;
    }

    /**
     * 대안을 한 번 생성하고 실제 마스터·목표 조건·비교 표본을 검증하여 일괄 분석 입력을 준비한다.
     * @param base 기준 CURRENT 스냅샷
     * @param baseAnalysis 동일 조건의 캐시 분석. 없으면 null
     * @param storedAnalysis 기존 저장 진단. 없으면 null
     * @param user 사용자 목표
     * @param scale 공통 기업 규모
     * @param references 추천에 허용하는 실제 마스터
     * @param analysisInputs 대안의 서버 계산 결과를 추가할 슬롯별 입력
     * @return 서로 다른 추천 스펙 두 개
     */
    private List<SpecProfileTargetScenarioDto> generateAlternatives(SpecProfileAnalyzationSnapshot base,
            SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis, SpecProfileAnnualSalaryAnalyzationResDto storedAnalysis,
            SpecProfileTargetScenarioDto user, CompanyGroupScale scale, SpecProfileTargetReferenceResDto references,
            Map<Integer, SpecProfileSimulationPreparedAnalysis> analysisInputs) {
        log.debug("[SpecProfileTargetService] AI 대안 생성 | generateAlternatives() - START | profileId: {}", base.specProfileId());
        OpenApiPrompt prompt = promptBuilder.buildTargetSpecRecommendationPrompt(base, baseAnalysis,
                storedAnalysis, user, scale, references);
        SpecProfileTargetRecommendationAiResDto recommendation = openApiUtil.request(prompt, SpecProfileTargetRecommendationAiResDto.class);
        try {
            /* (1) 사용자 목표·다른 대안과의 중복 및 실제 참조 ID를 검증한다. */
            recommendationValidator.validate(recommendation, user.changes());
            recommendationValidator.validateReferences(recommendation, references);
            List<SpecProfileTargetScenarioDto> alternatives = new ArrayList<>();
            /* (2) 대안별 기존 표본 계산을 재사용하되 개별 AI 호출은 수행하지 않는다. */
            for (SpecProfileTargetRecommendationAiResDto.Option option : recommendation.options()) {
                SpecProfileAnalyzationSnapshot snapshot = preparationService.prepareChanges(base, mapper.toSimulationChanges(option.changes()));
                analysisInputs.put(alternatives.size() + 2, analyzeService.prepareAnalysis(snapshot, scale, base));
                alternatives.add(SpecProfileTargetScenarioDto.builder().title(option.title()).changes(option.changes())
                        .snapshot(snapshot).build());
            }
            log.debug("[SpecProfileTargetService] AI 대안 생성 | generateAlternatives() - END | options: {}", alternatives.size());
            return List.copyOf(alternatives);
        } catch (CustomException exception) {
            if (exception.getErrorCode() instanceof SpecProfileErrorCode
                    || exception.getErrorCode() == SpecProfileAnalyzationErrorCode.INSUFFICIENT_MATCHED_SAMPLES
                    || exception.getErrorCode() == SpecProfileAnalyzationErrorCode.INVALID_CLASSIFICATION_SCORE) {
                throw new CustomException(SpecProfileTargetErrorCode.INVALID_RECOMMENDATION);
            }
            throw exception;
        }
    }

    private record UserSimulation(SpecProfileAnnualSalaryAnalyzationResDto base, SpecProfileSimulationAnalysis user) {}
}
