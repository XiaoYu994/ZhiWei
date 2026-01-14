package com.smallfish.zhiwei.config;

import com.smallfish.zhiwei.client.MilvusClientFactory;
import com.smallfish.zhiwei.common.constant.MilvusConstants;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.LoadCollectionParam;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
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
    public MilvusServiceClient milvusClient() {
        this.milvusClient = milvusClientFactory.createClient();
        return this.milvusClient;
    }


    /**
     * 应用启动后自动加载 Collection
     * CommandLineRunner 会在 Spring 容器启动完成后立即执行
     */
    @Bean
    public CommandLineRunner loadMilvusCollection(MilvusServiceClient milvusClient) {
        return args -> {
            String collectionName = MilvusConstants.MILVUS_COLLECTION_NAME;

            try {
                // 发起加载请求
                var response = milvusClient.loadCollection(
                        LoadCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build()
                );
                if (response.getStatus() != R.Status.Success.getCode()) {
                    log.error("Milvus 集合加载失败! code: {}, msg: {}", response.getStatus(), response.getMessage());
                } else {
                    log.info("Milvus 集合加载请求已发送 (异步加载中)");
                }
            } catch (Exception e) {
                log.error("Milvus 加载集合时发生异常", e);
            }
        };
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
