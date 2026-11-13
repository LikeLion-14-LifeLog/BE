package com.lifelog.domain.specprofile.event;

import com.lifelog.domain.specprofile.config.SpecProfileAnalyzationExecutorConfig;
import com.lifelog.domain.specprofile.service.openApi.OpenApiRequestManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpecProfileAnnualSalaryAnalyzationEventHandler {
    private final OpenApiRequestManager openApiRequestManager;

    /**
     * 커밋된 연봉 분석 요청을 전용 실행 풀에서 처리한다.
     * @param event 분석 실행 ID를 포함한 이벤트
     */
    @Async(SpecProfileAnalyzationExecutorConfig.EXECUTOR_NAME)
    @EventListener
    public void handle(SpecProfileAnuualSalaryAnalyzationRequestEvent event) {
        log.info("[SpecProfileAnnualSalaryAnalyzationEventHandler] handle() - START | runId: {}", event.runId());
        openApiRequestManager.putAnnualSalaryAnalyzationRequest(event);
        log.info("[SpecProfileAnnualSalaryAnalyzationEventHandler] handle() - END | runId: {}", event.runId());
    }
}
