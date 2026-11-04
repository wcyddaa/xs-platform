package com.xingshu.learning.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.learning.service.AbilityService;
import com.xingshu.learning.service.IStudentQuestionLogService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学生数据统一查询接口（供 FastAPI 智能体使用）
 */
@RestController
@RequestMapping("/api/student-data")
public class StudentDataController {

    private final IStudentQuestionLogService questionLogService;
    private final AbilityService abilityService;

    public StudentDataController(IStudentQuestionLogService questionLogService, AbilityService abilityService) {
        this.questionLogService = questionLogService;
        this.abilityService = abilityService;
    }

    /** 获取学生全量数据（供 AI 智能体使用） */
    @GetMapping("/{studentId}/full")
    public ApiResponse<Map<String, Object>> getFullData(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long classId) {
        var questions = questionLogService.getByStudentId(studentId);
        var abilities = classId != null ? abilityService.getStudentAbility(studentId, classId) : null;
        var overall = classId != null ? abilityService.getOverallMastery(studentId, classId) : null;
        return ApiResponse.success(Map.of(
                "studentId", studentId,
                "classId", classId != null ? classId : 0,
                "questions", questions,
                "abilities", abilities != null ? abilities : java.util.Collections.emptyList(),
                "overallMastery", overall != null ? overall : java.math.BigDecimal.ZERO
        ));
    }
}