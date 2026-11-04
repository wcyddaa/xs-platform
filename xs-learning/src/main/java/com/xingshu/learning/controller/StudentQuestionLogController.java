package com.xingshu.learning.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.learning.domain.dto.QuestionLogCreateDTO;
import com.xingshu.learning.domain.dto.QuestionLogQueryDTO;
import com.xingshu.learning.domain.po.StudentQuestionLog;
import com.xingshu.learning.domain.vo.ClassQuestionStatsVO;
import com.xingshu.learning.domain.vo.StudentQuestionStatsVO;
import com.xingshu.learning.domain.vo.QuestionTendencyVO;
import com.xingshu.learning.service.IStudentQuestionLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生提问记录管理 Controller
 */
@RestController
@RequestMapping("/api/question-log")
public class StudentQuestionLogController {

    private final IStudentQuestionLogService questionLogService;

    public StudentQuestionLogController(IStudentQuestionLogService questionLogService) {
        this.questionLogService = questionLogService;
    }

    /** 记录学生提问（FastAPI调用） */
    @PostMapping
    public ApiResponse<StudentQuestionLog> record(@RequestBody QuestionLogCreateDTO dto) {
        return ApiResponse.success(questionLogService.recordQuestion(dto));
    }

    /** 分页查询提问记录 */
    @GetMapping
    public ApiResponse<PageResult<StudentQuestionLog>> queryPage(QuestionLogQueryDTO dto) {
        return ApiResponse.success(questionLogService.queryPage(dto));
    }

    /** 教学班提问统计 */
    @GetMapping("/class/{classId}/stats")
    public ApiResponse<ClassQuestionStatsVO> getClassStats(@PathVariable Long classId) {
        return ApiResponse.success(questionLogService.getClassStats(classId));
    }

    /** 教学班学生提问次数统计 */
    @GetMapping("/class/{classId}/students")
    public ApiResponse<List<StudentQuestionStatsVO>> getStudentStats(@PathVariable Long classId) {
        return ApiResponse.success(questionLogService.getStudentStats(classId));
    }

    /** 教学班提问倾向分析 */
    @GetMapping("/class/{classId}/tendencies")
    public ApiResponse<List<QuestionTendencyVO>> getTendencies(@PathVariable Long classId) {
        return ApiResponse.success(questionLogService.getTendencies(classId));
    }

    /** 按教学班删除提问记录（级联删除调用） */
    @DeleteMapping("/class/{classId}")
    public ApiResponse<Void> deleteByClassId(@PathVariable Long classId) {
        questionLogService.deleteByClassId(classId);
        return ApiResponse.success("删除成功", null);
    }

    /** 按学生删除提问记录（级联删除调用） */
    @DeleteMapping("/student/{studentId}")
    public ApiResponse<Void> deleteByStudentId(@PathVariable Long studentId) {
        questionLogService.deleteByStudentId(studentId);
        return ApiResponse.success("删除成功", null);
    }
}