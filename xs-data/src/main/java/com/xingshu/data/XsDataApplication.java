package com.xingshu.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.xingshu.data"})
public class XsDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(XsDataApplication.class, args);
    }
}