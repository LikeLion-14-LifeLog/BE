package com.lifelog.domain.specprofile.service.specprofilesimulation;

import com.lifelog.domain.specprofile.dto.SpecProfileSimulationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class SpecProfileSimulationCacheRegistry {
    private final ConcurrentMap<Long, SpecProfileSimulationContext> cache = new ConcurrentHashMap<>();

    /**
     * 회원의 최신 완료 시뮬레이션으로 교체한다.
     * @param memberId 인증 회원 ID
     * @param context 스냅샷·예측 조건·분석 결과
     */
    public void store(Long memberId, SpecProfileSimulationContext context) {
        log.debug("[SpecProfileSimulationCacheRegistry] 응답 캐싱 | store() - START | memberId: {}", memberId);
        cache.put(memberId, context);
        log.debug("[SpecProfileSimulationCacheRegistry] 응답 캐싱 | store() - END | memberId: {}", memberId);
    }

    /**
     * 회원의 최신 완료 시뮬레이션을 조회한다.
     * @param memberId 인증 회원 ID
     * @return 캐시가 없으면 빈 Optional
     */
    public Optional<SpecProfileSimulationContext> find(Long memberId) {
        log.debug("[SpecProfileSimulationCacheRegistry] 캐시 조회 | find() - START | memberId: {}", memberId);
        Optional<SpecProfileSimulationContext> result = Optional.ofNullable(cache.get(memberId));
        log.debug("[SpecProfileSimulationCacheRegistry] 캐시 조회 | find() - END | memberId: {}", memberId);
        return result;
    }

    /**
     * 재사용하면 안 되는 회원 캐시를 제거한다.
     * @param memberId 인증 회원 ID
     */
    public void evict(Long memberId) {
        log.debug("[SpecProfileSimulationCacheRegistry] 캐시 제거 | evict() - START | memberId: {}", memberId);
        cache.remove(memberId);
        log.debug("[SpecProfileSimulationCacheRegistry] 캐시 제거 | evict() - END | memberId: {}", memberId);
    }
}
