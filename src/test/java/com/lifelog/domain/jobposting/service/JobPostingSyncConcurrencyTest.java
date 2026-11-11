package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.JobPostingSyncState;
import com.lifelog.domain.jobposting.dto.JobPostingSyncStatusResponse;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

// 실제 백그라운드 실행기로 비동기 동작을 확인하므로 @Transactional을 붙이지 않음 (외부 API 실패로 끝나 DB 변경 없음)
@SpringBootTest
class JobPostingSyncConcurrencyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JobPostingSyncService jobPostingSyncService;

    @MockitoBean
    private SeoulJobPostingFetcher fetcher;

    @Test
    void 시작하면_바로_반환하고_백그라운드에서_트랜잭션_밖으로_외부_API_호출() throws Exception {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        given(fetcher.fetch()).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        });

        JobPostingSyncStatusResponse started = jobPostingSyncService.start();
        assertThat(started.startedAt()).isNotNull();

        JobPostingSyncStatusResponse finished = awaitFinished();
        assertThat(finished.status()).isEqualTo(JobPostingSyncState.FAILED);
        assertThat(finished.errorCode()).isEqualTo("JOB_POSTING_502");
        assertThat(finished.finishedAt()).isNotNull();
        assertThat(transactionActive).isFalse();
    }

    @Test
    void 실행_중에_다시_시작하면_SYNC_IN_PROGRESS이고_끝나면_다시_시작_가능() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(fetcher.fetch()).willAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        });

        JobPostingSyncStatusResponse started = jobPostingSyncService.start();
        assertThat(started.status()).isEqualTo(JobPostingSyncState.RUNNING);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(jobPostingSyncService.getStatus().status()).isEqualTo(JobPostingSyncState.RUNNING);

        assertThatThrownBy(jobPostingSyncService::start)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(JobPostingErrorCode.SYNC_IN_PROGRESS);

        release.countDown();
        assertThat(awaitFinished().status()).isEqualTo(JobPostingSyncState.FAILED);

        // given(mock.fetch())로 다시 stub하면 기존 answer가 실행되므로 willThrow().given() 사용
        willThrow(new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED)).given(fetcher).fetch();
        jobPostingSyncService.start();
        assertThat(awaitFinished().status()).isEqualTo(JobPostingSyncState.FAILED);
    }

    private JobPostingSyncStatusResponse awaitFinished() throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            JobPostingSyncStatusResponse status = jobPostingSyncService.getStatus();
            if (status.status() != JobPostingSyncState.RUNNING) {
                return status;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("수집이 " + TIMEOUT + " 안에 끝나지 않음");
    }
}
