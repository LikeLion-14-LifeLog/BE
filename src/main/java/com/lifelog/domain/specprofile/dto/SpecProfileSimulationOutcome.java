package com.lifelog.domain.specprofile.dto;

/** 클라이언트 응답과 내부 캐시 저장 결과. */
public record SpecProfileSimulationOutcome(
        SpecProfileSimulationResDto response,
        boolean cached
) {}
