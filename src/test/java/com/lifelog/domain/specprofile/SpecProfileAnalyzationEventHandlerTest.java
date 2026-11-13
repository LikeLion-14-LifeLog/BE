package com.lifelog.domain.specprofile;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.lifelog.domain.specprofile.config.SpecProfileAnalyzationExecutorConfig;
import com.lifelog.domain.specprofile.event.SpecProfileAnnualSalaryAnalyzationEventHandler;
import com.lifelog.domain.specprofile.event.SpecProfileAnuualSalaryAnalyzationRequestEvent;
import com.lifelog.domain.specprofile.service.openApi.OpenApiRequestManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig({SpecProfileAnalyzationExecutorConfig.class, SpecProfileAnnualSalaryAnalyzationEventHandler.class,
        OpenApiRequestManager.class})
class SpecProfileAnalyzationEventHandlerTest {
    @Autowired ApplicationEventPublisher publisher;

    @Test
    @DisplayName("연봉 분석 이벤트는 발행 스레드와 다른 전용 실행 풀에서 처리한다")
    void 이벤트발행_전용스레드처리_성공() throws Exception {
        // given
        BlockingQueue<String> completedThreads = new LinkedBlockingQueue<>();
        Logger logger = (Logger) LoggerFactory.getLogger(SpecProfileAnnualSalaryAnalyzationEventHandler.class);
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                if (event.getFormattedMessage().contains("handle() - END | runId: 101")) {
                    completedThreads.add(event.getThreadName());
                }
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            // when
            publisher.publishEvent(new SpecProfileAnuualSalaryAnalyzationRequestEvent(101L));
            // then
            String workerThread = completedThreads.poll(5, TimeUnit.SECONDS);
            assertThat(workerThread).isNotNull().startsWith("spec-salary-analysis-")
                    .isNotEqualTo(Thread.currentThread().getName());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
