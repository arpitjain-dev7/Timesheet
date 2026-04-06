package com.timesheetManagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Enables Spring @Async and registers the dedicated email thread pool.
 *
 * <p>Implements {@link AsyncConfigurer} so the {@code emailTaskExecutor} bean
 * is also the default executor used when {@code @Async} is used without a
 * specific executor name. This prevents the "no qualifying bean of type
 * Executor" startup error and ensures all @Async tasks go to the right pool.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

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
        log.info("[ASYNC_CONFIG] emailTaskExecutor initialized — " +
                 "corePoolSize=2, maxPoolSize=5, queueCapacity=100, threadPrefix=email-async-");
        return executor;
    }

    /**
     * Makes emailTaskExecutor the default executor for all {@code @Async} methods
     * that do not specify an executor name.
     */
    @Override
    public Executor getAsyncExecutor() {
        return emailTaskExecutor();
    }

    /**
     * Catches any unchecked exception that escapes an {@code @Async} method
     * (i.e. is NOT caught inside the method's own try-catch). Without this,
     * such exceptions are silently discarded by Spring.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("[ASYNC_UNCAUGHT] Exception in async method '{}' with params {}: {}",
                        method.getName(),
                        Arrays.toString(params),
                        ex.getMessage(), ex);
    }
}
