package com.skillsphere.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

/**
 * Asynchronous execution for work that must never sit on a request.
 *
 * <p>Backed by <b>virtual threads</b>. The tasks queued here — SMTP handshakes,
 * analytics writes, model inference for a viva — are almost entirely blocked on
 * I/O, which is precisely the workload virtual threads exist for. A fixed
 * platform-thread pool would need careful sizing and would still stall every
 * queued task behind one slow SMTP server; virtual threads let each task block
 * independently at negligible cost.
 *
 * <p>The uncaught-exception handler matters more than it looks. A failure inside
 * an {@code @Async void} method is otherwise swallowed silently, so a mail
 * server outage would produce no error anywhere and registration emails would
 * simply stop arriving with nothing in the logs to explain it.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean("applicationTaskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("async-");
        executor.setVirtualThreads(true);
        // Bounded so a runaway producer cannot exhaust memory by queueing work
        // faster than it can be drained.
        executor.setConcurrencyLimit(200);
        return executor;
    }

    @Bean
    public AsyncTaskExecutor asyncTaskExecutor() {
        return (AsyncTaskExecutor) getAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async execution failed in {}.{}",
                    method.getDeclaringClass().getSimpleName(), method.getName(), throwable);
            new SimpleAsyncUncaughtExceptionHandler().handleUncaughtException(throwable, method, params);
        };
    }
}
