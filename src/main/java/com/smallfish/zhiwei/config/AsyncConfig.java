package com.smallfish.zhiwei.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/*
 * 异步线程池配置类
 * */
@Configuration
@EnableAsync
public class AsyncConfig {
    /**
     定义一个专门用于“知识库处理”的线程池
     *  专门处理知识库文件上传
     */
    @Bean("kbExecutor")

    public Executor kbExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 1. 核心线程数：平时保留的线程数 (根据你的 CPU 核数调整，IO 密集型一般设为 CPU * 2)
        executor.setCorePoolSize(4);

        // 2. 最大线程数：忙碌时最多创建多少线程
        executor.setMaxPoolSize(16);

        // 3. 队列容量：线程都忙时，新任务放在哪里排队
        executor.setQueueCapacity(200);

        // 4. 线程前缀：方便在日志里排查问题 (例如：KB-Async-1)
        executor.setThreadNamePrefix("KB-Async-");

        // 5. 拒绝策略：当队列满了且线程达到最大值，新任务怎么办？
        // CallerRunsPolicy: 让提交任务的主线程（Controller线程）自己去执行，起到“削峰填谷”和“反压”的作用，防止丢数据
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 6. 优雅停机：应用关闭时，等待任务执行完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * 定义一个名为 "aiTaskExecutor" 的线程池
     * 专门用于处理耗时的 AI 诊断任务
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 1. 获取 CPU 核数
        int processors = Runtime.getRuntime().availableProcessors();

        // 2. 动态计算核心参数
        // 核心线程数：保持 CPU + 2
        int corePoolSize = processors + 2;

        // 最大线程数：至少是核心数的 2 倍，且不能小于 20 (兜底)
        int maxPoolSize = Math.max(corePoolSize * 2, 20);

        executor.setCorePoolSize(corePoolSize);
        // 2. 最大线程数：防止突发流量
        executor.setMaxPoolSize(maxPoolSize);

        // 3. 队列容量：缓冲等待的任务
        executor.setQueueCapacity(100);

        // 4. 线程前缀：方便日志排查 (如：ai-ops-1, ai-ops-2)
        executor.setThreadNamePrefix("ai-ops-");

        // 5. 拒绝策略：当队列满了且线程也满了
        // CallerRunsPolicy: 由调用者所在的线程（即 Controller 线程）来执行
        // 这样会变相降低接口吞吐量，从而起到限流作用，保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 6. 优雅停机：应用关闭时，等待任务执行完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
