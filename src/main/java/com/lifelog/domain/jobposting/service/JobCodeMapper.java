package com.lifelog.domain.jobposting.service;

import java.util.Map;

/**
 * 고용24 6자리 모집직종코드 → 8차 고용직업분류 4자리(job.code) 변환 규칙.
 * 131x~133x는 앞 4자리가 같고, 134x~137x는 고용24가 이전 분류 번호를 써서 예외 변환표로 바꾼다.
 */
final class JobCodeMapper {

    private static final Map<String, String> EXCEPTIONS = Map.ofEntries(
            Map.entry("134100", "1342"), // 네트워크 시스템 개발자 → 네트워크 시스템 개발자
            Map.entry("134200", "1350"), // 정보보안 전문가 → 정보보안 전문가
            Map.entry("134900", "1349"), // 기타 네트워크 및 정보 보안 전문가 → 기타 데이터 및 네트워크 전문가
            Map.entry("135101", "1341"), // 데이터 설계 및 프로그래머 → 데이터 전문가
            Map.entry("135102", "1341"), // 데이터베이스 운영·관리자 → 데이터 전문가
            Map.entry("136101", "1343"), // 정보시스템 운영자 → 정보시스템 운영자
            Map.entry("136102", "1343"), // 네트워크 관리자 → 정보시스템 운영자 (실제 공고가 관제·운영)
            Map.entry("136103", "1343"), // IT 기술지원 전문가 → 정보시스템 운영자
            Map.entry("136200", "1344"), // 웹 운영자 → 웹 운영자
            Map.entry("137000", "1360")  // 통신 및 방송 송출 장비 기사 → 통신·방송송출 장비 기사
    );

    private JobCodeMapper() {
    }

    // 6자리가 아니면 null
    static String toJobCode(String externalJobCode) {
        if (externalJobCode == null || !externalJobCode.matches("\\d{6}")) {
            return null;
        }
        return EXCEPTIONS.getOrDefault(externalJobCode, externalJobCode.substring(0, 4));
    }
}
