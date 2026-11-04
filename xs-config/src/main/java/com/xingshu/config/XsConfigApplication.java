package com.xingshu.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.xingshu.config", "com.xingshu.common"})
public class XsConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(XsConfigApplication.class, args);
    }
}