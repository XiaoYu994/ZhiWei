package com.smallfish.zhiwei.config;

import com.smallfish.zhiwei.client.MilvusClientFactory;
import io.milvus.client.MilvusServiceClient;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
*  Milvus 配置类
*  管理和创建 MilvusServiceClient Bean
* */
@Slf4j
@Configuration
public class MilvusConfig {

    @Resource
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;


    /**
     *  创建 MilvusServiceClient Bean
     * @return MilvusServiceClient 实例
     */
    @Bean
    public MilvusServiceClient getMilvusClient() {
        return milvusClientFactory.createClient();
    }

    /*
    *  应用关闭时清理资源
    * */
    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            milvusClient.close();
            log.info("Milvus 客户端连接已关闭");
        }
    }
}
