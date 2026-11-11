package com.lifelog.domain.jobposting.scheduler;

import com.lifelog.domain.jobposting.service.JobPostingSyncService;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본 비활성화. job-posting.sync.scheduler.enabled=true일 때만 빈을 등록 (@EnableScheduling은 global/config)
@Slf4j
@Component
@ConditionalOnProperty(name = "job-posting.sync.scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
public class JobPostingSyncScheduler {

    private final JobPostingSyncService jobPostingSyncService;

    @Scheduled(cron = "${job-posting.sync.scheduler.cron}", zone = "Asia/Seoul")
    public void syncDaily() {
        try {
            jobPostingSyncService.sync();
        } catch (CustomException e) {
            log.warn("[JobPostingSyncScheduler] 정기 수집 실패 | {}", e.getErrorCode().getCode());
        } catch (RuntimeException e) {
            log.error("[JobPostingSyncScheduler] 정기 수집 중 예외", e);
        }
    }
}
