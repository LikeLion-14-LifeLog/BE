package com.lifelog.domain.jobposting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class JobPostingSyncExecutorConfig {

    public static final String EXECUTOR_BEAN = "jobPostingSyncExecutor";

    // 수집은 한 번에 하나만 실행하므로 전용 스레드 1개 (요청 스레드와 분리)
    // 반환 타입은 Executor (테스트에서 동기 실행기로 바꿔 끼울 수 있게), 종료 시 shutdown 호출
    @Bean(name = EXECUTOR_BEAN, destroyMethod = "shutdown")
    public Executor jobPostingSyncExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "job-posting-sync");
            thread.setDaemon(true);
            return thread;
        });
    }
}
