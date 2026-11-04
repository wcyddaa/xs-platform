package com.xingshu.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.xingshu.notification", "com.xingshu.common"})
public class XsNotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(XsNotificationApplication.class, args);
    }
}