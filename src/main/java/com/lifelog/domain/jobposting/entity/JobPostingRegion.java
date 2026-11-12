package com.lifelog.domain.jobposting.entity;

import lombok.Getter;

import java.util.List;

/**
 * 지역 필터. region_code(시군구 코드 5자리)의 앞자리 또는 정확한 코드로 판단.
 * 강원(42→51, 2023)·전북(45→52, 2024)은 특별자치도 전환으로 바뀐 코드까지 포함.
 * 현재 수집원(서울 일자리포털)에는 서울·경기·인천 공고만 있음.
 */
@Getter
public enum JobPostingRegion {
    SEOUL("서울 전체", List.of("11"), List.of()),
    // 판교는 성남시 분당구(41135)로 판단
    SEOUL_PANGYO("서울·판교 전체", List.of("11"), List.of("41135")),
    GYEONGGI("경기 전체", List.of("41"), List.of()),
    INCHEON("인천", List.of("28"), List.of()),
    BUSAN("부산", List.of("26"), List.of()),
    DAEGU("대구", List.of("27"), List.of()),
    GWANGJU("광주", List.of("29"), List.of()),
    DAEJEON("대전", List.of("30"), List.of()),
    ULSAN("울산", List.of("31"), List.of()),
    SEJONG("세종", List.of("36"), List.of()),
    GANGWON("강원", List.of("42", "51"), List.of()),
    CHUNGBUK("충북", List.of("43"), List.of()),
    CHUNGNAM("충남", List.of("44"), List.of()),
    JEONBUK("전북", List.of("45", "52"), List.of()),
    JEONNAM("전남", List.of("46"), List.of()),
    GYEONGBUK("경북", List.of("47"), List.of()),
    GYEONGNAM("경남", List.of("48"), List.of()),
    JEJU("제주", List.of("50"), List.of());

    private final String label;
    private final List<String> codePrefixes;
    private final List<String> exactCodes;

    JobPostingRegion(String label, List<String> codePrefixes, List<String> exactCodes) {
        this.label = label;
        this.codePrefixes = codePrefixes;
        this.exactCodes = exactCodes;
    }
}
