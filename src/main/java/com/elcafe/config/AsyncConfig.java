package com.elcafe.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements SchedulingConfigurer {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(callerRunsWithLoggingPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated scheduler thread pool for @Scheduled tasks.
     * Without this, scheduled tasks hijack the WebSocket MessageBroker threads,
     * which are limited to 1-2 threads and cause contention.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // ~30 registered jobs, several at 1-minute cadence; 4 threads meant one slow daily batch could
        // starve the minute jobs (PERF-10). 8 keeps sub-minute jobs on time under batch overlap.
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
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
