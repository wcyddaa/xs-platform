package com.xingshu.api.client.course;

import com.xingshu.common.base.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 课程服务 Feign 客户端
 */
@FeignClient(name = "xs-course", url = "${xs.api.course-url:http://localhost:8082}")
public interface CourseClient {

    @GetMapping("/api/classes/student/{studentId}")
    ApiResponse<List<ClassDTO>> getClassesByStudent(@PathVariable Long studentId);

    @GetMapping("/api/classes/teacher/{teacherId}")
    ApiResponse<List<ClassDTO>> getClassesByTeacher(@PathVariable Long teacherId);
}