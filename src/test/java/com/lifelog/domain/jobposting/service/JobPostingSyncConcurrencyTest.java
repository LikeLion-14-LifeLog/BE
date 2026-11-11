package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

// 트랜잭션 경계와 동시 실행을 확인하므로 @Transactional을 붙이지 않음 (외부 API 실패로 끝나 DB 변경 없음)
@SpringBootTest
class JobPostingSyncConcurrencyTest {

    @Autowired
    private JobPostingSyncService jobPostingSyncService;

    @MockitoBean
    private SeoulJobPostingFetcher fetcher;

    @Test
    void 외부_API_호출은_트랜잭션_밖에서_실행() {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        given(fetcher.fetch()).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        });

        assertThatThrownBy(jobPostingSyncService::sync).isInstanceOf(CustomException.class);
        assertThat(transactionActive).isFalse();
    }

    @Test
    void 수집_중에_다시_호출하면_SYNC_IN_PROGRESS이고_끝나면_다시_실행_가능() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(fetcher.fetch()).willAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(jobPostingSyncService::sync);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(jobPostingSyncService::sync)
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(JobPostingErrorCode.SYNC_IN_PROGRESS);

            release.countDown();
            assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(CustomException.class);
        } finally {
            executor.shutdownNow();
        }

        // given(mock.fetch())로 다시 stub하면 기존 answer가 실행되므로 willThrow().given() 사용
        willThrow(new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED)).given(fetcher).fetch();
        assertThatThrownBy(jobPostingSyncService::sync)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(JobPostingErrorCode.EXTERNAL_API_FAILED);
    }
}
