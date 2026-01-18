package com.smallfish.zhiwei.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/*
*  文档分片配置
* */
@Data
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkProperties {

    /*
    *  每个分片的最大字符数
    * */
    private int maxSize;

    /*
    *  分片之间的重叠字符树
    * */
    private int overlap;
}
