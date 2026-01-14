package com.smallfish.zhiwei.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    /*
     *  文件存储路径
     * */
    private String path;

    /*
     *  文件支持的格式
     * */
    private List<String> allowedExtensions;

    //  判断文件格式是否支持
    public boolean isAllowed(String extension) {
        if (extension == null || allowedExtensions == null) {
            return false;
        }
        // List 自带 contains 方法
        // 注意：YAML 里配的是小写，这里建议也转小写对比
        return allowedExtensions.contains(extension.toLowerCase());
    }
}
