package com.lifelog.domain.major.service;

import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.domain.major.mapper.MajorMapper;
import com.lifelog.domain.major.repository.MajorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = MajorInitServiceTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MajorServiceTest {

    @Autowired private MajorService majorService;
    @Autowired private MajorRepository majorRepository;
    @Autowired private DataSource dataSource;
    @MockitoSpyBean private MajorCsvReader majorCsvReader;
    @MockitoSpyBean private MajorMapper majorMapper;

    @BeforeAll
    void prepareSharedDatabase() throws Exception {
        MajorTestDatabaseSupport.initialize(dataSource);
    }

    @AfterEach
    void cleanUp() {
        majorRepository.deleteAllInBatch();
        assertThat(majorRepository.count()).isZero();
    }

    @Test
    @DisplayName("실제 CSV 읽기에는 트랜잭션이 없고 저장 처리에만 쓰기 트랜잭션이 적용된다")
    void CSV읽기와_저장_트랜잭션_분리_성공() {
        /* given: 실제 동작을 유지하는 Spy로 각 단계의 트랜잭션 상태만 관찰한다. */
        List<Boolean> csvTransactionStates = new ArrayList<>();
        AtomicBoolean allRowsInWriteTransaction = new AtomicBoolean(true);
        AtomicInteger mappedRowCount = new AtomicInteger();
        doAnswer(invocation -> {
            csvTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            MajorCsvReadResult source = (MajorCsvReadResult) invocation.callRealMethod();
            csvTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return source;
        }).when(majorCsvReader).read();
        doAnswer(invocation -> {
            csvTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            MajorCsvReadResult source = (MajorCsvReadResult) invocation.callRealMethod();
            csvTransactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return source;
        }).when(majorCsvReader).parse(any(Reader.class));
        doAnswer(invocation -> {
            boolean writeTransactionActive = TransactionSynchronizationManager.isActualTransactionActive()
                    && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            if (!writeTransactionActive) {
                allRowsInWriteTransaction.set(false);
            }
            mappedRowCount.incrementAndGet();
            return invocation.callRealMethod();
        }).when(majorMapper).toEntity(any());
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        /* when: MajorService가 실제 파일 읽기와 실제 저장 트랜잭션을 순서대로 호출한다. */
        MajorInitResDto result = majorService.initFromCsv();

        /* then: 읽기 전후에는 트랜잭션이 없고 모든 저장 대상의 처리 중에는 쓰기 트랜잭션이 있다. */
        assertThat(csvTransactionStates).containsExactly(false, false, false, false);
        assertThat(mappedRowCount.get()).isEqualTo(37654);
        assertThat(allRowsInWriteTransaction.get()).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(result).isEqualTo(MajorInitResDto.create(37656, 2, 37654, 37654, 0, 0));
        /* 서비스 반환 후 별도 조회에서도 데이터가 보여 실제 커밋이 완료되었음을 확인한다. */
        assertThat(majorRepository.count()).isEqualTo(37654);
    }
}
