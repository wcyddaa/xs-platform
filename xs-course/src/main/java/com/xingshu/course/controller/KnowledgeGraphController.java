package com.xingshu.course.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.course.service.IKnowledgeGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 课程知识图谱：教师与学生共用
 */
@RestController
@RequestMapping("/api/courses/{courseId}/knowledge-graph")
public class KnowledgeGraphController {

    private final IKnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphController(IKnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /** 查询课程知识图谱 */
    @GetMapping
    public ApiResponse<Map<String, Object>> get(@PathVariable Long courseId) {
        return ApiResponse.success(knowledgeGraphService.getGraph(courseId));
    }

    /** 基于课件、学生提问与作业错误重新生成课程知识图谱 */
    @PostMapping("/extract")
    public ApiResponse<Map<String, Object>> extract(@PathVariable Long courseId) {
        return ApiResponse.success(knowledgeGraphService.extractGraph(courseId));
    }
}
