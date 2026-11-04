package com.xingshu.api.client.learning;

import com.xingshu.common.base.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 学习服务 Feign 客户端
 */
@FeignClient(name = "xs-learning", url = "${xs.api.learning-url:http://localhost:8087}")
public interface LearningClient {

    @GetMapping("/api/student-data/{studentId}/full")
    ApiResponse<Map<String, Object>> getStudentFullData(@PathVariable Long studentId);
}