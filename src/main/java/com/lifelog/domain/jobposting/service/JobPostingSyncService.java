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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        List<SeoulJobRow> rows = seoulJobApiClient.fetchAll();
        // 빈 응답으로 기존 공고가 전부 마감 처리되는 것을 막음
        if (rows.isEmpty()) {
            log.error("[JobPostingSyncService] 원본 공고가 0건이라 수집을 중단");
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        }

        LocalDate today = LocalDate.now(SEOUL);
        Map<String, JobPostingSyncItem> items = new LinkedHashMap<>();
        int skippedCount = 0;
        for (SeoulJobRow row : rows) {
            try {
                JobPostingSyncItem item = mapper.toItem(row, today);
                items.putIfAbsent(item.externalId(), item);
            } catch (RuntimeException e) {
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
        closed += writer.closeMissing(items.keySet());

        JobPostingSyncResponse response = new JobPostingSyncResponse(
                items.size(), created, updated, unchanged, closed);
        log.info("[JobPostingSyncService] 채용공고 수집 | sync() - END | rows: {}, skipped: {}, result: {}",
                rows.size(), skippedCount, response);
        return response;
    }
}
