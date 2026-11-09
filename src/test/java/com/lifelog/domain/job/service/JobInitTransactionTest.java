package com.lifelog.domain.job.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// 테스트 트랜잭션이 있으면 경계를 확인할 수 없으므로 @Transactional을 붙이지 않음 (빈 CSV라 DB 변경 없음)
@SpringBootTest
class JobInitTransactionTest {

    @Autowired
    private JobService jobService;

    @MockitoBean
    private JobCsvReader jobCsvReader;

    @Test
    void CSV_읽기는_트랜잭션_밖에서_실행() {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        given(jobCsvReader.read()).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of();
        });

        jobService.initFromCsv();

        assertThat(transactionActive).isFalse();
    }
}
