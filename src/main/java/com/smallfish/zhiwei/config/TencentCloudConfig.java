package com.smallfish.zhiwei.config;

import com.tencentcloudapi.common.Credential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/*
*  腾讯云 cls 配置类
* */
@Configuration
public class TencentCloudConfig {
    // 只提供 Credential Bean，因为 Client 要动态创建
    @Bean
    public Credential clsCredential(
            @Value("${tencent.cls.access-key}") String secretId,
            @Value("${tencent.cls.secret-key}") String secretKey) {
        return new Credential(secretId, secretKey);
    }
}