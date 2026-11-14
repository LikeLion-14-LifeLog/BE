package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.event.SpecProfileAnuualSalaryAnalyzationRequestEvent;
import com.lifelog.domain.specprofile.service.openApi.OpenApiRequestManager;
import com.lifelog.domain.specprofile.service.openApi.OpenApiRequestWorker;
import com.lifelog.domain.specprofile.service.SpecProfileAnnualSalaryAnalyzationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OpenApiRequestWorkerTest {
    private final OpenApiRequestManager manager = new OpenApiRequestManager();
    private final SpecProfileAnnualSalaryAnalyzationService service = mock(SpecProfileAnnualSalaryAnalyzationService.class);
    private final OpenApiRequestWorker worker = new OpenApiRequestWorker(manager, service);

    @AfterEach
    void 종료() {
        worker.stop();
    }

    @Test
    @DisplayName("적재된 요청을 전용 워커에서 등록 순서대로 실행한다")
    void 여러요청_등록순서처리_성공() throws Exception {
        // given
        BlockingQueue<Long> processed = new LinkedBlockingQueue<>();
        BlockingQueue<String> threads = new LinkedBlockingQueue<>();
        doAnswer(invocation -> {
            threads.add(Thread.currentThread().getName());
            processed.add(invocation.getArgument(0));
            return null;
        }).when(service).analyze(anyLong());
        for (long runId = 1; runId <= 3; runId++) {
            manager.putAnnualSalaryAnalyzationRequest(new SpecProfileAnuualSalaryAnalyzationRequestEvent(runId));
        }

        // when
        worker.start();

        // then
        assertThat(processed.poll(5, TimeUnit.SECONDS)).isEqualTo(1L);
        assertThat(processed.poll(5, TimeUnit.SECONDS)).isEqualTo(2L);
        assertThat(processed.poll(5, TimeUnit.SECONDS)).isEqualTo(3L);
        assertThat(threads).containsOnly("open-api-request-worker");
    }

    @Test
    @DisplayName("빈 큐에서 대기하다가 들어온 요청을 처리하고 실패 후에도 다음 요청을 처리한다")
    void 대기중요청추가_실패후에도계속처리_성공() throws Exception {
        // given
        BlockingQueue<Long> attempted = new LinkedBlockingQueue<>();
        doAnswer(invocation -> {
            Long runId = invocation.getArgument(0);
            attempted.add(runId);
            if (runId == 1L) {
                throw new IllegalStateException("테스트 작업 실패");
            }
            return null;
        }).when(service).analyze(anyLong());
        worker.start();

        // when
        manager.putAnnualSalaryAnalyzationRequest(new SpecProfileAnuualSalaryAnalyzationRequestEvent(1L));
        manager.putAnnualSalaryAnalyzationRequest(new SpecProfileAnuualSalaryAnalyzationRequestEvent(2L));

        // then
        assertThat(attempted.poll(5, TimeUnit.SECONDS)).isEqualTo(1L);
        assertThat(attempted.poll(5, TimeUnit.SECONDS)).isEqualTo(2L);
    }

    @Test
    @DisplayName("빈 큐에서 대기 중인 워커를 종료하면 이후 요청을 실행하지 않는다")
    void 대기워커종료_이후실행없음_성공() {
        // given
        worker.start();

        // when
        worker.stop();
        manager.putAnnualSalaryAnalyzationRequest(new SpecProfileAnuualSalaryAnalyzationRequestEvent(3L));

        // then
        verify(service, never()).analyze(anyLong());
    }
}
