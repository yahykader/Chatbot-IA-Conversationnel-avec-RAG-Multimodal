package com.exemple.transactionservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enable scheduling for WebSocket cleanup task
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Empty config class just to enable @Scheduled
}