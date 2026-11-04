package com.xingshu.course;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.xingshu.course.mapper")
@SpringBootApplication(scanBasePackages = {"com.xingshu.course", "com.xingshu.common"})
public class XsCourseApplication {
    public static void main(String[] args) {
        SpringApplication.run(XsCourseApplication.class, args);
    }
}