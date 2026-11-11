package com.lifelog.global.infra.seoul;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 서울 열린데이터 조회 결과.
 * @param totalCount 원본 전체 건수 (list_total_count, 데이터가 없으면 0)
 * @param rows       받은 행 (row 배열의 원소)
 */
public record SeoulOpenApiPage(int totalCount, List<JsonNode> rows) {

    public static SeoulOpenApiPage empty() {
        return new SeoulOpenApiPage(0, List.of());
    }

    // 전체 건수만큼 받았는지
    public boolean isComplete() {
        return rows.size() >= totalCount;
    }
}
