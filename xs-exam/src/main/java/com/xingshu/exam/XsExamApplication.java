package com.xingshu.exam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.xingshu.exam.mapper")
@SpringBootApplication(scanBasePackages = {"com.xingshu.exam", "com.xingshu.common"})
public class XsExamApplication {
    public static void main(String[] args) {
        SpringApplication.run(XsExamApplication.class, args);
    }
}