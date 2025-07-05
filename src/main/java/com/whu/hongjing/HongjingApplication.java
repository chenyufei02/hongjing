package com.whu.hongjing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.whu.hongjing.mapper")
@EnableScheduling
public class HongjingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HongjingApplication.class, args);
    }

}
