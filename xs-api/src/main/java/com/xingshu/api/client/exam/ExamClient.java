package com.xingshu.api.client.exam;

import com.xingshu.common.base.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 作业服务 Feign 客户端
 */
@FeignClient(name = "xs-exam", url = "${xs.api.exam-url:http://localhost:8084}")
public interface ExamClient {

    @GetMapping("/api/homework/templates/teacher/stats")
    ApiResponse<Long> getTeacherStats(@RequestParam Long teacherId);
}