package com.lifelog.domain.specprofile.service.specprofilesimulation;

import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAnalysis;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationContext;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationOutcome;
import com.lifelog.domain.specprofile.mapper.SpecProfileSimulationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileSimulationService {
    private final SpecProfileSimulationPreparationService preparationService;
    private final SpecProfileSimulationAnalyzeService analyzeService;
    private final SpecProfileSimulationCacheRegistry cacheRegistry;
    private final SpecProfileSimulationMapper simulationMapper;

    /**
     * 현재·목표 연봉을 계산하고 완료 결과를 회원별 메모리 캐시에 보관하며 DB에는 저장하지 않는다.
     * @param memberId 인증 회원 ID
     * @param request 추가할 목표 조건과 공통 기업 규모
     * @return 현재·목표 연봉과 캐싱 성공 여부
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpecProfileSimulationOutcome simulate(Long memberId, SpecProfileSimulationReqDto request) {
        log.info("[SpecProfileSimulationService] 연봉 시뮬레이션 | simulate() - START | memberId: {}", memberId);
        /* (1) 읽기 트랜잭션을 완료한 뒤 독립된 현재·목표 스냅샷을 받는다. */
        SpecProfileSimulationContext context = preparationService.prepare(memberId, request);
        /* (2) 동일 기업 규모 조건으로 현재·목표를 분석한다. 실패는 호출자에게 그대로 전달한다. */
        SpecProfileSimulationAnalysis base = analyzeService.analyze(context.base(), request.companyGroupScale(), context.base());
        SpecProfileSimulationAnalysis simulated = context.simulated() == null ? null
                : analyzeService.analyze(context.simulated(), request.companyGroupScale(), context.base());
        /* (3) 저장 작업 없이 연차별 예측과 목표 준비 정보를 응답에 포함한다. */
        SpecProfileSimulationResDto result = simulationMapper.toResDto(context.base().specProfileId(),
                request.companyGroupScale(), base, simulated);
        /* (4) 캐싱 실패도 계산된 응답은 정상 반환한다. */
        boolean cached = cacheCompletedSimulation(memberId, context, request, base, simulated, result);
        log.info("[SpecProfileSimulationService] 연봉 시뮬레이션 | simulate() - END | profileId: {}", result.baseSpecProfileId());
        return new SpecProfileSimulationOutcome(result, cached);
    }

    /**
     * 완료 결과를 캐싱하고 실패 시 이전 캐시를 무효화한다.
     * @param memberId 인증 회원 ID
     * @param context 분석에 사용한 스냅샷
     * @param request 예측 조건
     * @param base 현재 분석
     * @param simulated 목표 분석
     * @param response 목표 준비 정보를 포함한 응답
     * @return 캐시 저장 성공 여부
     */
    private boolean cacheCompletedSimulation(Long memberId, SpecProfileSimulationContext context,
            SpecProfileSimulationReqDto request, SpecProfileSimulationAnalysis base,
            SpecProfileSimulationAnalysis simulated, SpecProfileSimulationResDto response) {
        log.debug("[SpecProfileSimulationService] 완료 결과 캐싱 | cacheCompletedSimulation() - START | memberId: {}", memberId);
        try {
            cacheRegistry.store(memberId,
                    simulationMapper.toCachedContext(context, request.companyGroupScale(), base, simulated, response));
            log.debug("[SpecProfileSimulationService] 완료 결과 캐싱 | cacheCompletedSimulation() - END | cached: true");
            return true;
        } catch (RuntimeException exception) {
            log.warn("[SpecProfileSimulationService] 완료 결과 캐싱 실패 | memberId: {}", memberId, exception);
            try {
                cacheRegistry.evict(memberId);
            } catch (RuntimeException evictionException) {
                log.warn("[SpecProfileSimulationService] 이전 캐시 무효화 실패 | memberId: {}", memberId, evictionException);
            }
            log.debug("[SpecProfileSimulationService] 완료 결과 캐싱 | cacheCompletedSimulation() - END | cached: false");
            return false;
        }
    }
}
