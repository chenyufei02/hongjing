package com.whu.hongjing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.whu.hongjing.mapper")
@EnableScheduling
@EnableRetry
public class HongjingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HongjingApplication.class, args);
    }

}
