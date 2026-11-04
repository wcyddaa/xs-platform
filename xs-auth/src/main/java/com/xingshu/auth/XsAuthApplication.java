package com.xingshu.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.xingshu.auth", "com.xingshu.common"})
@MapperScan("com.xingshu.auth.mapper")
public class XsAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(XsAuthApplication.class, args);
    }
}