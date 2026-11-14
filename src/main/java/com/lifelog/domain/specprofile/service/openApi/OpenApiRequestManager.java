package com.lifelog.domain.specprofile.service.openApi;

import com.lifelog.domain.specprofile.dto.OpenApiRequest;
import com.lifelog.domain.specprofile.event.SpecProfileAnuualSalaryAnalyzationRequestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class OpenApiRequestManager {
    private final BlockingQueue<OpenApiRequest> queue = new LinkedBlockingQueue<>();

    /**
     * 연봉 분석 요청을 큐에 적재한다.
     * 여러 비동기 이벤트 처리 스레드에서 동시에 호출할 수 있다.
     * @param request 큐에 적재할 OpenApiRequest 구현 객체
     */
    public void putAnnualSalaryAnalyzationRequest(SpecProfileAnuualSalaryAnalyzationRequestEvent request) {
        log.info("[OpenApiRequestManager] 연봉 분석 요청 큐 적재 | putAnnualSalaryAnalyzationRequest() - START | runId: {}", request.runId());
        queue.offer(request);
        log.info("[OpenApiRequestManager] 연봉 분석 요청 큐 적재 | putAnnualSalaryAnalyzationRequest() - END | runId: {}", request.runId());
    }

    /**
     * 가장 먼저 적재된 요청을 꺼내며 큐가 비어 있으면 요청이 들어올 때까지 대기한다.
     * @return 큐에서 꺼낸 요청
     * @throws InterruptedException 대기 중 워커 종료 등으로 인터럽트된 경우
     */
    public OpenApiRequest take() throws InterruptedException {
        log.debug("[OpenApiRequestManager] 요청 대기 | take() - START");
        OpenApiRequest request = queue.take();
        log.debug("[OpenApiRequestManager] 요청 수신 | take() - END");
        return request;
    }
}
