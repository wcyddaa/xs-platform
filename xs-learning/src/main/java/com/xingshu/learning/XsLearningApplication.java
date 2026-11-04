package com.xingshu.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.xingshu.learning", "com.xingshu.common"})
public class XsLearningApplication {
    public static void main(String[] args) {
        SpringApplication.run(XsLearningApplication.class, args);
    }
}