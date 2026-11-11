package com.lifelog.domain.jobposting.service;

import java.util.Map;

/**
 * 근무 지역명의 시도를 짧은 이름으로 통일 (예: "경기도 화성시" → "경기 화성시", "서울특별시 강남구" → "서울 강남구").
 */
final class RegionNameNormalizer {

    private static final Map<String, String> SHORT_NAMES = Map.ofEntries(
            Map.entry("서울특별시", "서울"), Map.entry("서울시", "서울"),
            Map.entry("부산광역시", "부산"), Map.entry("부산시", "부산"),
            Map.entry("대구광역시", "대구"), Map.entry("대구시", "대구"),
            Map.entry("인천광역시", "인천"), Map.entry("인천시", "인천"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전광역시", "대전"), Map.entry("대전시", "대전"),
            Map.entry("울산광역시", "울산"), Map.entry("울산시", "울산"),
            Map.entry("세종특별자치시", "세종"), Map.entry("세종시", "세종"),
            Map.entry("경기도", "경기"),
            Map.entry("강원도", "강원"), Map.entry("강원특별자치도", "강원"),
            Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"),
            Map.entry("전라북도", "전북"), Map.entry("전북특별자치도", "전북"),
            Map.entry("전라남도", "전남"),
            Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주"), Map.entry("제주도", "제주")
    );

    private RegionNameNormalizer() {
    }

    // 첫 단어(시도)만 바꾸고 나머지는 그대로
    static String normalize(String regionName) {
        if (regionName == null) {
            return null;
        }
        int space = regionName.indexOf(' ');
        String sido = space < 0 ? regionName : regionName.substring(0, space);
        String rest = space < 0 ? "" : regionName.substring(space);
        return SHORT_NAMES.getOrDefault(sido, sido) + rest;
    }
}
