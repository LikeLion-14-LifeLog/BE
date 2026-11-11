package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.SeoulJobRow;

import java.util.List;
import java.util.Set;

/**
 * recMntList 조회 결과.
 * @param rows                  읽은 공고
 * @param unreadableExternalIds 행 형식이 달라 읽지 못한 공고의 externalId (마감 대상에서 제외)
 * @param complete              원본 전체 건수만큼 받았는지 (false면 마감 처리를 건너뜀)
 */
public record SeoulJobFetchResult(List<SeoulJobRow> rows, Set<String> unreadableExternalIds, boolean complete) {

    public boolean isEmpty() {
        return rows.isEmpty() && unreadableExternalIds.isEmpty();
    }
}
