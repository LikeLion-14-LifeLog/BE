package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.client.SeoulJobApiClient;
import com.lifelog.domain.jobposting.client.SeoulJobRow;
import com.lifelog.domain.jobposting.dto.JobPostingSyncResponse;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SeoulJobApiClient seoulJobApiClient;
    private final SeoulJobPostingMapper mapper;
    private final JobPostingSyncWriter writer;

    // 수집(외부 호출 포함 수 분)이 끝날 때까지 다른 요청·스케줄러를 막음. 서버 1대 기준
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 서울 일자리포털 공고 전체를 받아 저장한다.
     * 외부 API 호출은 트랜잭션 밖에서 모두 끝내고, 저장은 1000건 단위 트랜잭션으로 나눠 수행한다.
     * 중간에 실패해도 다시 실행하면 같은 결과로 수렴한다 (externalId 기준 upsert).
     */
    public JobPostingSyncResponse sync() {
        if (!running.compareAndSet(false, true)) {
            throw new CustomException(JobPostingErrorCode.SYNC_IN_PROGRESS);
        }
        try {
            return doSync();
        } finally {
            running.set(false);
        }
    }

    private JobPostingSyncResponse doSync() {
        log.info("[JobPostingSyncService] 채용공고 수집 | sync() - START");
        // 전 페이지를 받지 못하면 여기서 EXTERNAL_API_FAILED로 끝나 저장·마감 처리를 하지 않음
        List<SeoulJobRow> rows = seoulJobApiClient.fetchAll();
        // 빈 응답으로 기존 공고가 전부 마감 처리되는 것을 막음
        if (rows.isEmpty()) {
            log.warn("[JobPostingSyncService] 원본 공고가 0건이라 저장·마감 처리를 건너뜀");
            return new JobPostingSyncResponse(0, 0, 0, 0, 0);
        }

        LocalDate today = LocalDate.now(SEOUL);
        Map<String, JobPostingSyncItem> items = new LinkedHashMap<>();
        // 변환 실패 행도 포함한 "이번에 본 공고". 마감 대상에서 제외
        Set<String> seenExternalIds = new HashSet<>();
        int skippedCount = 0;
        for (SeoulJobRow row : rows) {
            // 변환 실패와 관계없이 식별할 수 있도록 원본 문자열로 먼저 계산
            String externalId = SeoulJobPostingMapper.externalId(row);
            seenExternalIds.add(externalId);
            if (items.containsKey(externalId)) {
                continue;
            }
            try {
                items.put(externalId, mapper.toItem(row, today));
            } catch (RuntimeException e) {
                // 기존 데이터는 그대로 둠
                skippedCount++;
                log.warn("[JobPostingSyncService] 변환 실패로 건너뜀 | company: {}, title: {} | {}",
                        row.company(), row.title(), e.toString());
            }
        }

        List<JobPostingSyncItem> itemList = new ArrayList<>(items.values());
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int closed = 0;
        for (int from = 0; from < itemList.size(); from += JobPostingSyncWriter.CHUNK_SIZE) {
            JobPostingSyncWriter.ChunkResult result = writer.upsert(
                    itemList.subList(from, Math.min(from + JobPostingSyncWriter.CHUNK_SIZE, itemList.size())));
            created += result.createdCount();
            updated += result.updatedCount();
            unchanged += result.unchangedCount();
            closed += result.closedCount();
        }
        closed += writer.closeMissing(seenExternalIds);

        JobPostingSyncResponse response = new JobPostingSyncResponse(
                items.size(), created, updated, unchanged, closed);
        log.info("[JobPostingSyncService] 채용공고 수집 | sync() - END | rows: {}, skipped: {}, result: {}",
                rows.size(), skippedCount, response);
        return response;
    }
}
