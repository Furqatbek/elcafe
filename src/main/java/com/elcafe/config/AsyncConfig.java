package com.elcafe.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(callerRunsWithLoggingPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Custom rejection handler that logs a warning and runs the task in the caller thread.
     * This provides back-pressure and prevents task loss when the executor is overloaded.
     */
    private RejectedExecutionHandler callerRunsWithLoggingPolicy() {
        return (runnable, executor) -> {
            if (!executor.isShutdown()) {
                log.warn("Async task queue full ({}/{} slots used), executing in caller thread. " +
                        "Consider increasing queue capacity or thread pool size.",
                        executor.getQueue().size(),
                        executor.getQueue().size() + executor.getQueue().remainingCapacity());
                runnable.run();
            } else {
                log.error("Async executor is shutting down, task rejected: {}", runnable);
            }
        };
    }
}
