package com.smallfish.zhiwei;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/*
*  项目启动类
* */
@EnableRetry
@SpringBootApplication
public class ZhiWeiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhiWeiApplication.class, args);
    }
}