package com.timesheetManagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring's @Async support and configures a dedicated thread pool
 * for background tasks such as email sending.
 *
 * Using a dedicated pool prevents email I/O from consuming threads that
 * serve HTTP requests.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool used by @Async methods (e.g. EmailService.sendHtmlEmail).
     * Pool parameters are conservative; tune via application.yaml in production.
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

