package com.lifelog.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 스케줄링 인프라만 켬. 각 스케줄러는 자체 설정값(@ConditionalOnProperty)으로 활성화
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
