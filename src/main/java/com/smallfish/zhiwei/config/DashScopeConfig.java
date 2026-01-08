package com.smallfish.zhiwei.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class DashScopeConfig {


    @Value("${spring.ai.dashscope.chat.options.timeout:180000}")
    private long timeout;

    /**
     * 配置 RestClient.Builder，设置超时时间
     * Spring AI 会自动使用这个 Bean
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        // 1. 配置 JDK HttpClient (负责连接超时)
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();

        // 2. 创建工厂并配置读取超时 (JdkClientHttpRequestFactory 分离了这两种超时)
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeout));

        // 3. 构建 RestClient
        return RestClient.builder()
                .requestFactory(requestFactory);
    }
}