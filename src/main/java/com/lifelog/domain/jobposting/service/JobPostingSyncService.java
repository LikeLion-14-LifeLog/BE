package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.config.JobPostingSyncExecutorConfig;
import com.lifelog.domain.jobposting.dto.JobPostingSyncResponse;
import com.lifelog.domain.jobposting.dto.JobPostingSyncStatusResponse;
import com.lifelog.domain.jobposting.dto.SeoulJobRow;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class JobPostingSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SeoulJobPostingFetcher fetcher;
    private final SeoulJobPostingMapper mapper;
    private final JobPostingSyncWriter writer;
    private final Executor executor;

    // 수집(외부 호출 포함 수 분)이 끝날 때까지 다른 요청·스케줄러를 막음. 서버 1대 기준
    private final AtomicBoolean running = new AtomicBoolean(false);
    // 마지막 실행 상태 (서버 메모리, 재시작하면 IDLE)
    private final AtomicReference<JobPostingSyncStatusResponse> lastStatus =
            new AtomicReference<>(JobPostingSyncStatusResponse.idle());

    public JobPostingSyncService(SeoulJobPostingFetcher fetcher, SeoulJobPostingMapper mapper,
                                 JobPostingSyncWriter writer,
                                 @Qualifier(JobPostingSyncExecutorConfig.EXECUTOR_BEAN) Executor executor) {
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.writer = writer;
        this.executor = executor;
    }

    /**
     * 수집을 백그라운드에서 시작하고 바로 현재 상태를 반환한다 (API·스케줄러 공통 진입점).
     * 이미 실행 중이면 SYNC_IN_PROGRESS. 결과는 {@link #getStatus()}로 확인한다.
     */
    public JobPostingSyncStatusResponse start() {
        if (!running.compareAndSet(false, true)) {
            throw new CustomException(JobPostingErrorCode.SYNC_IN_PROGRESS);
        }
        LocalDateTime startedAt = LocalDateTime.now(SEOUL);
        lastStatus.set(JobPostingSyncStatusResponse.running(startedAt));
        try {
            executor.execute(() -> run(startedAt));
        } catch (RejectedExecutionException e) {
            log.error("[JobPostingSyncService] 수집 작업을 시작하지 못함", e);
            lastStatus.set(JobPostingSyncStatusResponse.failed(startedAt, LocalDateTime.now(SEOUL),
                    CommonErrorCode.INTERNAL_SERVER_ERROR.getCode()));
            running.set(false);
        }
        return lastStatus.get();
    }

    public JobPostingSyncStatusResponse getStatus() {
        return lastStatus.get();
    }

    // 상태를 먼저 기록한 뒤 실행 중 표시를 해제해야 다음 실행이 이전 결과를 덮어쓰지 않음
    private void run(LocalDateTime startedAt) {
        try {
            JobPostingSyncResponse result = sync();
            lastStatus.set(JobPostingSyncStatusResponse.succeeded(startedAt, LocalDateTime.now(SEOUL), result));
        } catch (CustomException e) {
            log.warn("[JobPostingSyncService] 채용공고 수집 실패 | {}", e.getErrorCode().getCode());
            lastStatus.set(JobPostingSyncStatusResponse.failed(startedAt, LocalDateTime.now(SEOUL),
                    e.getErrorCode().getCode()));
        } catch (RuntimeException e) {
            log.error("[JobPostingSyncService] 채용공고 수집 중 예외", e);
            lastStatus.set(JobPostingSyncStatusResponse.failed(startedAt, LocalDateTime.now(SEOUL),
                    CommonErrorCode.INTERNAL_SERVER_ERROR.getCode()));
        } finally {
            running.set(false);
        }
    }

    /**
     * 서울 일자리포털 공고 전체를 받아 저장한다.
     * 외부 API 호출은 트랜잭션 밖에서 모두 끝내고, 저장은 1000건 단위 트랜잭션으로 나눠 수행한다.
     * 페이지 호출이 하나라도 실패하면 저장 없이 EXTERNAL_API_FAILED.
     * 호출은 모두 성공했지만 전체 건수보다 적게 받았으면 받은 공고만 저장하고 마감 처리는 건너뛴다 (complete=false).
     */
    private JobPostingSyncResponse sync() {
        log.info("[JobPostingSyncService] 채용공고 수집 | sync() - START");
        SeoulJobFetchResult fetched = fetcher.fetch();
        // 빈 응답으로 기존 공고가 전부 마감 처리되는 것을 막음
        if (fetched.isEmpty()) {
            log.warn("[JobPostingSyncService] 원본 공고가 0건이라 저장·마감 처리를 건너뜀");
            return JobPostingSyncResponse.empty();
        }

        LocalDate today = LocalDate.now(SEOUL);
        Map<String, JobPostingSyncItem> items = new LinkedHashMap<>();
        // 읽기·변환 실패 행도 포함한 "이번에 본 공고". 마감 대상에서 제외
        Set<String> seenExternalIds = new HashSet<>(fetched.unreadableExternalIds());
        int skippedCount = fetched.unreadableExternalIds().size();
        for (SeoulJobRow row : fetched.rows()) {
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

        boolean complete = fetched.complete();
        if (complete) {
            closed += writer.closeMissing(seenExternalIds);
        } else {
            log.warn("[JobPostingSyncService] 원본 전체 건수보다 적게 받아 마감 처리를 건너뜀 | rows: {}",
                    fetched.rows().size());
        }

        JobPostingSyncResponse response = new JobPostingSyncResponse(
                items.size(), created, updated, unchanged, closed, skippedCount, complete);
        log.info("[JobPostingSyncService] 채용공고 수집 | sync() - END | fetched: {}, result: {}",
                fetched.rows().size(), response);
        return response;
    }
}
