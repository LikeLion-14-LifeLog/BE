package com.lifelog.domain.specprofile.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class SpecProfileAnalyzationExecutorConfig {
    public static final String EXECUTOR_NAME = "specProfileAnnualSalaryAnalyzationExecutor";

    /** 연봉 분석 이벤트 전용 실행 풀을 구성한다. @return 크기가 제한된 비동기 실행 풀 */
    @Bean(name = EXECUTOR_NAME)
    public ThreadPoolTaskExecutor specProfileAnnualSalaryAnalyzationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("spec-salary-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
