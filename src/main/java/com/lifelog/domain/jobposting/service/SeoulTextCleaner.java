package com.lifelog.domain.jobposting.service;

import java.util.regex.Pattern;

/**
 * 서울 일자리포털 원본 텍스트 정리 규칙을 한곳에 모은 클래스 (저장할 때 적용).
 * - 원본이 표현하지 못한 글자를 FULLWIDTH QUESTION MARK(U+FF1F, "？")로 보내므로 치환
 *   업종명은 가운뎃점(·), 그 외 텍스트는 공백
 * - 줄바꿈은 \n으로 통일 (\r\n, \r 혼재)
 * - 같은 줄 안의 연속 공백(탭·NBSP 등 포함)은 1칸, 줄 앞뒤 공백과 전체 앞뒤 공백 제거
 * - 결과가 비어 있으면 null
 */
final class SeoulTextCleaner {

    static final String BROKEN_CHAR = "？";
    private static final String INDUSTRY_REPLACEMENT = "·";
    private static final String TEXT_REPLACEMENT = " ";

    // \h: 공백, 탭, NBSP(U+00A0), 전각 공백(U+3000) 등 가로 공백
    private static final Pattern HORIZONTAL_SPACES = Pattern.compile("\\h+");
    private static final Pattern SPACES_AROUND_NEWLINE = Pattern.compile(" ?\n ?");

    private SeoulTextCleaner() {
    }

    // 제목·본문·기업명 등 일반 텍스트
    static String clean(String value) {
        return clean(value, TEXT_REPLACEMENT);
    }

    // 업종명: "사업시설 유지？관리 서비스업" → "사업시설 유지·관리 서비스업"
    static String cleanIndustryName(String value) {
        return clean(value, INDUSTRY_REPLACEMENT);
    }

    private static String clean(String value, String replacement) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(BROKEN_CHAR, replacement);
        cleaned = HORIZONTAL_SPACES.matcher(cleaned).replaceAll(" ");
        cleaned = SPACES_AROUND_NEWLINE.matcher(cleaned).replaceAll("\n");
        cleaned = cleaned.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
