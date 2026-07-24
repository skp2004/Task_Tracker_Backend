package com.tasktracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * Configures Java 21 Virtual Threads (Project Loom) as the executor for:
 * <ul>
 *   <li>Spring {@code @Async} methods</li>
 *   <li>Spring MVC async request handling</li>
 *   <li>Background processing tasks</li>
 * </ul>
 *
 * <p>Virtual threads park (rather than block) during I/O, meaning thousands of
 * concurrent requests can be handled with minimal memory and without platform-thread
 * pool saturation. Combined with {@code spring.threads.virtual.enabled=true} in
 * application.yml this fully replaces the Tomcat platform-thread executor.</p>
 */
@Configuration
@EnableAsync
@Slf4j
public class VirtualThreadConfig {

    /**
     * Primary {@code @Async} executor backed by a Java 21 virtual thread per-task
     * executor. Replaces Spring Boot's default {@code SimpleAsyncTaskExecutor} with
     * a true virtual-thread executor that scales to millions of tasks.
     */
    @Bean(name = "applicationTaskExecutor")
    public AsyncTaskExecutor virtualThreadExecutor() {
        log.info("✅ Configuring Virtual Thread executor (Java 21 Project Loom)");
        return new TaskExecutorAdapter(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}
