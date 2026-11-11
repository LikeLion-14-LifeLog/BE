package com.lifelog.domain.jobposting.entity;

import lombok.Getter;

import java.util.List;

// 지역 필터. region_code(시군구 코드 5자리)의 앞자리 또는 정확한 코드로 판단
@Getter
public enum JobPostingRegion {
    SEOUL("서울 전체", List.of("11"), List.of()),
    // 판교는 성남시 분당구(41135)로 판단
    SEOUL_PANGYO("서울·판교 전체", List.of("11"), List.of("41135")),
    GYEONGGI("경기 전체", List.of("41"), List.of()),
    INCHEON("인천", List.of("28"), List.of()),
    BUSAN("부산", List.of("26"), List.of());

    private final String label;
    private final List<String> codePrefixes;
    private final List<String> exactCodes;

    JobPostingRegion(String label, List<String> codePrefixes, List<String> exactCodes) {
        this.label = label;
        this.codePrefixes = codePrefixes;
        this.exactCodes = exactCodes;
    }
}
