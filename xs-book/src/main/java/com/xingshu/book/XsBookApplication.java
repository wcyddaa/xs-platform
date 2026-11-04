package com.xingshu.book;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.xingshu.book", "com.xingshu.common"})
public class XsBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(XsBookApplication.class, args);
    }

}