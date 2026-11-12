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

    // API와 같은 실행 로직(start)을 사용. 결과는 수집 상태 조회 API와 로그로 확인
    @Scheduled(cron = "${job-posting.sync.scheduler.cron}", zone = "Asia/Seoul")
    public void syncDaily() {
        try {
            jobPostingSyncService.start();
        } catch (CustomException e) {
            // 이미 실행 중(SYNC_IN_PROGRESS)이면 이번 회차는 건너뜀
            log.warn("[JobPostingSyncScheduler] 정기 수집을 시작하지 않음 | {}", e.getErrorCode().getCode());
        }
    }
}
