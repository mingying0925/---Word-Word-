package com.skillbridge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置。
 * <p>
 * 为 {@code @Async} 注解提供有界的 {@link ThreadPoolTaskExecutor}，
 * 替代 Spring 默认的 {@code SimpleAsyncTaskExecutor}（每次新建线程、无池、无上限）。
 * <p>
 * 拒绝策略采用 {@link ThreadPoolExecutor.CallerRunsPolicy}：
 * 当队列满时由调用线程同步执行，形成自然限流，避免任务丢失。
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * 导出任务专用线程池。
     * <ul>
     *   <li>核心线程数 2：与 Python 微服务 gunicorn --workers 2 对齐，避免过载</li>
     *   <li>最大线程数 4：允许小幅突发</li>
     *   <li>队列容量 50：缓冲突发导出请求</li>
     *   <li>线程名前缀 export-：便于日志排查</li>
     * </ul>
     */
    @Bean(name = "exportExecutor")
    public Executor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("导出任务线程池已初始化: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), 50);
        return executor;
    }

    /**
     * 默认异步执行器：供其他 @Async 方法使用。
     * 比导出池更宽松，但仍有上限，防止线程无节制增长。
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 指定 {@code @Async} 默认使用的执行器。
     * 优先使用通用的 taskExecutor。
     */
    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }
}
