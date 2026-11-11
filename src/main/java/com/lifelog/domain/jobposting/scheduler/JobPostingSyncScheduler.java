package com.lifelog.domain.jobposting.scheduler;

import com.lifelog.domain.jobposting.service.JobPostingSyncService;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// 기본 비활성화. job-posting.sync.scheduler.enabled=true일 때만 스케줄링 자체를 켬
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
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
