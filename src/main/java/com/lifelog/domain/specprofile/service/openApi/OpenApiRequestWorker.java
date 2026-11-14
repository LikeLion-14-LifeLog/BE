package com.lifelog.domain.specprofile.service.openApi;

import com.lifelog.domain.specprofile.dto.OpenApiRequest;
import com.lifelog.domain.specprofile.event.SpecProfileAnuualSalaryAnalyzationRequestEvent;
import com.lifelog.domain.specprofile.service.SpecProfileAnnualSalaryAnalyzationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiRequestWorker {
    private final OpenApiRequestManager requestManager;
    private final SpecProfileAnnualSalaryAnalyzationService annualSalaryAnalyzationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("open-api-request-worker").factory());
    private volatile boolean running;

    /** 애플리케이션 초기화 시 요청 큐를 소비하는 전용 워커를 시작한다. */
    @PostConstruct
    public void start() {
        log.info("[OpenApiRequestWorker] 워커 시작 | start() - START");
        running = true;
        executor.execute(this::consume);
        log.info("[OpenApiRequestWorker] 워커 시작 | start() - END");
    }

    /** 애플리케이션 종료 시 큐 대기와 실행 작업을 인터럽트하고 워커 종료를 기다린다. */
    @PreDestroy
    public void stop() {
        log.info("[OpenApiRequestWorker] 워커 종료 | stop() - START");
        running = false;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[OpenApiRequestWorker] 워커가 종료 대기 시간을 초과했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        log.info("[OpenApiRequestWorker] 워커 종료 | stop() - END");
    }

    /** 요청을 하나씩 꺼내 처리하며 작업 실패가 다음 요청 처리를 중단하지 않게 한다. */
    private void consume() {
        log.debug("[OpenApiRequestWorker] 요청 소비 | consume() - START");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                OpenApiRequest request = requestManager.take();
                execute(request);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                log.error("[OpenApiRequestWorker] 요청 처리 실패, 다음 요청을 계속 처리합니다.", exception);
            }
        }
        log.debug("[OpenApiRequestWorker] 요청 소비 | consume() - END");
    }

    /**
     * 요청 종류에 맞는 실행 서비스에 작업을 전달한다.
     * @param request 큐에서 꺼낸 요청
     */
    private void execute(OpenApiRequest request) {
        log.debug("[OpenApiRequestWorker] 작업 전달 | execute() - START");
        if (request instanceof SpecProfileAnuualSalaryAnalyzationRequestEvent event) {
            annualSalaryAnalyzationService.analyze(event.runId());
        } else {
            log.error("[OpenApiRequestWorker] 지원하지 않는 요청 타입 | type: {}", request.getClass().getName());
        }
        log.debug("[OpenApiRequestWorker] 작업 전달 | execute() - END");
    }
}
