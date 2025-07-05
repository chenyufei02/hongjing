package com.whu.hongjing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HongjingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HongjingApplication.class, args);
    }

}
