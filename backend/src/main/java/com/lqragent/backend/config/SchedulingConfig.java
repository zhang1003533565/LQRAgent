package com.lqragent.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务，供上传队列 worker 使用。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
