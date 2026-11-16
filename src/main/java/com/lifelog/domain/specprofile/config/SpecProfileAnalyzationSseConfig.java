package com.lifelog.domain.specprofile.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SpecProfileAnalyzationSseConfig {
    public static final String EXECUTOR_NAME = "specProfileAnalyzationSseExecutor";

    /** 분석 워커와 분리된 크기 제한 SSE 전송 풀을 구성한다. @return SSE 전송 실행 풀 */
    @Bean(name = EXECUTOR_NAME)
    public ThreadPoolTaskExecutor specProfileAnalyzationSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("spec-analysis-sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
