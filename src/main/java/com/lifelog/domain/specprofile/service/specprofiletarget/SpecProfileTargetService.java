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
import java.util.List;

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
     * 사용자 목표만 저장하고 세 선택지의 연봉·준비 정보는 저장 없이 반환한다.
     * @param memberId 인증 회원 ID
     * @param request 기준 CURRENT와 사용자 추가 목표
     * @return 사용자 슬롯 1과 저장하지 않은 AI 대안 슬롯 2·3
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileTargetSaveResDto saveTarget(Long memberId, SpecProfileTargetSaveReqDto request) {
        log.info("[SpecProfileTargetService] 목표 저장 및 대안 비교 | saveTarget() - START | memberId: {}", memberId);
        /* (1) 사용자 입력을 검증하고 하나의 CURRENT 스냅샷을 전체 비교에서 사용한다. */
        recommendationValidator.validateDates(request.changes());
        SpecProfileSimulationContext prepared = preparationService.prepare(memberId, mapper.toSimulationRequest(request));
        UserSimulation simulation = resolveUserSimulation(memberId, prepared, request.companyGroupScale());
        SpecProfileAnalyzationSnapshot target = prepared.simulated() == null ? prepared.base() : prepared.simulated();
        SpecProfileTargetScenarioDto user = SpecProfileTargetScenarioDto.builder()
                .title(mapper.toUserTitle(prepared.base(), target, request.changes())).changes(request.changes())
                .snapshot(target).analysis(simulation.user()).build();
        /* (2) 기존 CURRENT 진단은 읽기만 하며, 추천 및 대안별 재분석 결과는 요청 메모리에 유지한다. */
        SpecProfileAnnualSalaryAnalyzationResDto stored = queryService.findStoredCurrentAnalysis(memberId, prepared.base());
        SpecProfileTargetReferenceResDto references = referenceQueryService.findAllowedReferences(prepared.base(), target);
        List<SpecProfileTargetScenarioDto> alternatives = generateAlternatives(prepared.base(), simulation.base(), stored,
                user, request.companyGroupScale(), references);
        /* (3) 모든 AI 처리 후 사용자 TARGET과 스펙만 짧은 독립 트랜잭션에서 저장한다. */
        Long targetId = saveService.upsertUserTarget(memberId, prepared.base(), request.changes(), request.companyGroupScale());
        SpecProfileTargetSaveResDto response = mapper.toResponse(request, targetId, simulation.base(), user, alternatives);
        log.info("[SpecProfileTargetService] 목표 저장 및 대안 비교 | saveTarget() - END | targetId: {}", targetId);
        return response;
    }

    /** 동일 조건의 최근 완료 시뮬레이션을 재사용하고 누락·불일치 시 기존 분석기를 실행한다. */
    private UserSimulation resolveUserSimulation(Long memberId, SpecProfileSimulationContext prepared, CompanyGroupScale scale) {
        log.debug("[SpecProfileTargetService] 사용자 목표 분석 | resolveUserSimulation() - START | memberId: {}", memberId);
        SpecProfileSimulationContext cached = null;
        try {
            cached = cacheRegistry.find(memberId).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("[SpecProfileTargetService] 최근 캐시 조회 실패, 재분석 진행 | memberId: {}", memberId, exception);
        }
        UserSimulation result;
        if (cachePolicy.canReuse(prepared, cached, scale)) {
            result = new UserSimulation(cached.baseAnalysis(), mapper.toCachedUserAnalysis(cached));
        } else {
            SpecProfileSimulationAnalysis base = analyzeService.analyze(prepared.base(), scale, prepared.base());
            SpecProfileSimulationAnalysis user = prepared.simulated() == null ? base
                    : analyzeService.analyze(prepared.simulated(), scale, prepared.base());
            result = new UserSimulation(base.analysis(), user);
        }
        log.debug("[SpecProfileTargetService] 사용자 목표 분석 | resolveUserSimulation() - END | memberId: {}", memberId);
        return result;
    }

    /** 유효한 대안 두 개를 재분석·종합 평가하고 의미상 실패에 한해 한 번 재생성한다. */
    private List<SpecProfileTargetScenarioDto> generateAlternatives(SpecProfileAnalyzationSnapshot base,
            SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis, SpecProfileAnnualSalaryAnalyzationResDto storedAnalysis,
            SpecProfileTargetScenarioDto user, CompanyGroupScale scale, SpecProfileTargetReferenceResDto references) {
        log.debug("[SpecProfileTargetService] AI 대안 생성 | generateAlternatives() - START | profileId: {}", base.specProfileId());
        String feedback = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            SpecProfileTargetRecommendationAiResDto recommendation = null;
            SpecProfileTargetEvaluationAiResDto evaluation = null;
            try {
                OpenApiPrompt prompt = promptBuilder.buildTargetSpecRecommendationPrompt(base, baseAnalysis,
                        storedAnalysis, user, scale, references, feedback);
                recommendation = openApiUtil.request(prompt, SpecProfileTargetRecommendationAiResDto.class);
                recommendationValidator.validate(recommendation, user.changes());
                recommendationValidator.validateReferences(recommendation, references);
                List<SpecProfileTargetScenarioDto> alternatives = new ArrayList<>();
                for (SpecProfileTargetRecommendationAiResDto.Option option : recommendation.options()) {
                    SpecProfileAnalyzationSnapshot snapshot = preparationService.prepareChanges(base, mapper.toSimulationChanges(option.changes()));
                    SpecProfileSimulationAnalysis analysis = analyzeService.analyze(snapshot, scale, base);
                    alternatives.add(SpecProfileTargetScenarioDto.builder().title(option.title()).changes(option.changes())
                            .snapshot(snapshot).analysis(analysis).build());
                }
                evaluation = openApiUtil.request(
                        promptBuilder.buildTargetSpecEvaluationPrompt(base, baseAnalysis, user, alternatives, scale),
                        SpecProfileTargetEvaluationAiResDto.class);
                recommendationValidator.validateEvaluation(evaluation);
                log.debug("[SpecProfileTargetService] AI 대안 생성 | generateAlternatives() - END | options: {}", alternatives.size());
                return List.copyOf(alternatives);
            } catch (CustomException exception) {
                if (!canRegenerate(exception)) throw exception;
                if (attempt == 1) {
                    if (exception.getErrorCode() == SpecProfileTargetErrorCode.RECOMMENDATION_NOT_BETTER) throw exception;
                    throw new CustomException(SpecProfileTargetErrorCode.INVALID_RECOMMENDATION);
                }
                feedback = mapper.toValidationFeedback(exception.getMessage(), recommendation, evaluation);
                log.warn("[SpecProfileTargetService] 대안 검증 실패, 한 번 재생성 | profileId: {}, code: {}", base.specProfileId(), exception.getErrorCode().getCode());
            }
        }
        throw new CustomException(SpecProfileTargetErrorCode.INVALID_RECOMMENDATION);
    }

    /** 통신·인증·서버 오류를 재생성으로 숨기지 않고 대안 내용에 따른 실패만 재시도한다. */
    private boolean canRegenerate(CustomException exception) {
        log.debug("[SpecProfileTargetService] 재생성 가능 여부 | canRegenerate() - START");
        boolean result = exception.getErrorCode() instanceof SpecProfileTargetErrorCode
                || exception.getErrorCode() instanceof SpecProfileErrorCode
                || exception.getErrorCode() == SpecProfileAnalyzationErrorCode.INSUFFICIENT_MATCHED_SAMPLES
                || exception.getErrorCode() == SpecProfileAnalyzationErrorCode.INVALID_CLASSIFICATION_SCORE;
        log.debug("[SpecProfileTargetService] 재생성 가능 여부 | canRegenerate() - END | result: {}", result);
        return result;
    }

    private record UserSimulation(SpecProfileAnnualSalaryAnalyzationResDto base, SpecProfileSimulationAnalysis user) {}
}
