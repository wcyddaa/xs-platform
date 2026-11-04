package com.xingshu.log;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.xingshu.log.mapper")
@SpringBootApplication(scanBasePackages = {"com.xingshu.log", "com.xingshu.common"})
public class XsLogApplication {
    public static void main(String[] args) {
        SpringApplication.run(XsLogApplication.class, args);
    }
}