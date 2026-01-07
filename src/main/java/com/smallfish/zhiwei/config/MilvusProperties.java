package com.smallfish.zhiwei.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/*
*  milvus 配置类
* */
@Data
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private String host;
    private Integer port;
    private String username;
    private String password;
    private String database;
    private Long timeout;

    /*
    *  获取连接地址
    * */
    public String getAddress() {
        return host + ":" + port;
    }
}
