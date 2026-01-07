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
    // 定义一个专门用于“知识库处理”的线程池
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

        executor.initialize();
        return executor;
    }
}
