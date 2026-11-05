package com.xingshu.course.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.course.domain.po.ClassInfo;
import com.xingshu.course.domain.po.ClassStudent;
import com.xingshu.course.domain.po.ClassTeacher;
import com.xingshu.course.service.IClassInfoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final IClassInfoService classInfoService;

    public ClassController(IClassInfoService classInfoService) {
        this.classInfoService = classInfoService;
    }

    /**
     * 分页查询教学班
     */
    @GetMapping
    public ApiResponse<PageResult<ClassInfo>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        Page<ClassInfo> page = classInfoService.pageClasses(pageNum, pageSize, keyword, status);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 获取教师绑定的教学班列表
     */
    @GetMapping("/teacher/{teacherId}")
    public ApiResponse<PageResult<ClassInfo>> listByTeacher(@PathVariable Long teacherId,
                                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                                       @RequestParam(defaultValue = "100") Integer pageSize) {
        Page<ClassInfo> page = classInfoService.pageByTeacher(pageNum, pageSize, teacherId);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 获取教学班详情（含学生人数与教师姓名，供 AI 上下文注入使用）
     */
    @GetMapping("/{id}")
    public ApiResponse<ClassInfo> detail(@PathVariable Long id) {
        ClassInfo classInfo = classInfoService.getById(id);
        if (classInfo == null) {
            return ApiResponse.error(404, "教学班不存在");
        }
        List<ClassStudent> students = classInfoService.getStudents(id);
        classInfo.setStudentCount((long) students.size());
        List<ClassTeacher> teachers = classInfoService.getTeachers(id);
        if (!teachers.isEmpty()) {
            classInfo.setTeacherNames(teachers.stream()
                    .map(ClassTeacher::getTeacherName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("、")));
        }
        return ApiResponse.success(classInfo);
    }

    /**
     * 创建教学班
     */
    @PostMapping
    public ApiResponse<ClassInfo> create(@RequestBody ClassInfo classInfo) {
        if (classInfo.getStatus() == null) {
            classInfo.setStatus("draft");
        }
        // 显式设置创建/更新时间，确保不受 MyBatis-Plus 自动填充策略影响
        classInfo.setCreateTime(java.time.LocalDateTime.now());
        classInfo.setUpdateTime(java.time.LocalDateTime.now());
        // 自动填充教师姓名：如果 teacherName 为空或等于工号，从用户服务查询真实姓名
        classInfoService.fillTeacherName(classInfo);
        classInfoService.save(classInfo);
        // 创建者自动成为该教学班的教师（多对多关系）
        if (classInfo.getTeacherId() != null) {
            classInfoService.addTeacher(classInfo.getId(), classInfo.getTeacherId(), classInfo.getTeacherName());
        }
        return ApiResponse.success("教学班创建成功", classInfo);
    }

    /**
     * 更新教学班
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody ClassInfo classInfo) {
        classInfo.setId(id);
        // 如果更新包含教师信息，从用户服务获取真实姓名
        if (classInfo.getTeacherId() != null) {
            classInfoService.fillTeacherName(classInfo);
        }
        classInfoService.updateById(classInfo);
        return ApiResponse.success("更新成功", null);
    }

    /**
     * 删除教学班
     * @param role 删除者角色：admin/teacher（默认admin）
     * @param deleterId 删除者用户ID（教师删除时需传入）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                     @RequestParam(defaultValue = "admin") String role,
                                     @RequestParam(required = false) Long deleterId) {
        try {
            classInfoService.deleteClass(id, role, deleterId);
            return ApiResponse.success("删除成功", null);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除前预检查：返回教学班绑定的数据概览
     */
    @GetMapping("/{id}/pre-check")
    public ApiResponse<Map<String, Object>> preCheckDelete(@PathVariable Long id) {
        return ApiResponse.success(classInfoService.preCheckDelete(id));
    }

    /**
     * 审核通过教学班
     */
    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        classInfoService.approveClass(id);
        return ApiResponse.success("审核通过", null);
    }

    /**
     * 绑定教师到教学班（追加绑定，支持一个教学班多个教师）
     */
    @PutMapping("/{id}/teacher")
    public ApiResponse<Void> bindTeacher(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        Long teacherId = Long.valueOf(body.get("teacherId").toString());
        String teacherName = (String) body.getOrDefault("teacherName", "");
        classInfoService.addTeacher(id, teacherId, teacherName);
        return ApiResponse.success("绑定成功", null);
    }

    // ==================== 教学班教师管理（多对多） ====================

    /** 获取教学班绑定的教师列表 */
    @GetMapping("/{id}/teachers")
    public ApiResponse<List<ClassTeacher>> getTeachers(@PathVariable Long id) {
        return ApiResponse.success(classInfoService.getTeachers(id));
    }

    /** 添加教师到教学班（可绑定多个教师） */
    @PostMapping("/{id}/teachers")
    public ApiResponse<Void> addTeacher(@PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        Long teacherId = Long.valueOf(body.get("teacherId").toString());
        String teacherName = (String) body.getOrDefault("teacherName", "");
        classInfoService.addTeacher(id, teacherId, teacherName);
        return ApiResponse.success("添加成功", null);
    }

    /** 从教学班移除指定教师 */
    @DeleteMapping("/{id}/teachers/{teacherId}")
    public ApiResponse<Void> removeTeacher(@PathVariable Long id,
                                           @PathVariable Long teacherId) {
        classInfoService.removeTeacher(id, teacherId);
        return ApiResponse.success("已移除该教师", null);
    }

    /** 获取教师可见的教学班ID集合（分配 + 创建） */
    @GetMapping("/teacher/{teacherId}/class-ids")
    public ApiResponse<List<Long>> getTeacherClassIds(@PathVariable Long teacherId) {
        return ApiResponse.success(classInfoService.getTeacherClassIds(teacherId));
    }

    /**
     * 解除教师绑定
     */
    @DeleteMapping("/{id}/teacher")
    public ApiResponse<Void> unbindTeacher(@PathVariable Long id) {
        classInfoService.unbindTeacher(id);
        return ApiResponse.success("已解除教师绑定", null);
    }

    // ==================== 学生管理 ====================

    /** 学生自主选班（选上即绑定） */
    @PostMapping("/{classId}/enroll")
    public ApiResponse<Void> enroll(@PathVariable Long classId,
                                    @RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        String studentName = (String) body.getOrDefault("studentName", "");
        try {
            classInfoService.enrollStudent(classId, studentId, studentName);
            return ApiResponse.success("选课成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 添加学生到教学班
     */
    @PostMapping("/{classId}/students")
    public ApiResponse<Void> addStudent(@PathVariable Long classId,
                                         @RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        String studentName = (String) body.getOrDefault("studentName", "");
        try {
            classInfoService.addStudent(classId, studentId, studentName);
            return ApiResponse.success("添加成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 学生退出教学班
     */
    @DeleteMapping("/{classId}/students/{studentId}")
    public ApiResponse<Void> removeStudent(@PathVariable Long classId,
                                            @PathVariable Long studentId) {
        classInfoService.removeStudent(classId, studentId);
        return ApiResponse.success("移除成功", null);
    }

    /**
     * 获取教学班学生列表（含姓名）
     */
    @GetMapping("/{classId}/students")
    public ApiResponse<List<ClassStudent>> getStudents(@PathVariable Long classId) {
        return ApiResponse.success(classInfoService.getStudents(classId));
    }

    /**
     * 获取学生所属的教学班列表
     */
    @GetMapping("/student/{studentId}")
    public ApiResponse<List<ClassInfo>> getClassesByStudent(@PathVariable Long studentId) {
        return ApiResponse.success(classInfoService.getClassesByStudent(studentId));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.success(Map.of("classCount", classInfoService.count()));
    }

    /** 教师统计（教学班数+学生数） */
    @GetMapping("/teacher/{teacherId}/stats")
    public ApiResponse<Map<String, Long>> teacherStats(@PathVariable Long teacherId) {
        return ApiResponse.success(classInfoService.getTeacherStats(teacherId));
    }

    /** 获取教学班关联的课程列表 */
    @GetMapping("/{classId}/courses")
    public ApiResponse<List<com.xingshu.course.domain.po.Course>> getCourses(@PathVariable Long classId) {
        return ApiResponse.success(classInfoService.getCoursesByClassId(classId));
    }

    // ==================== 级联删除（供其他服务调用） ====================

    /** 按学生ID删除所有班级绑定关系 */
    @DeleteMapping("/student/{studentId}/bindings")
    public ApiResponse<Void> deleteStudentBindings(@PathVariable Long studentId) {
        classInfoService.deleteStudentBindings(studentId);
        return ApiResponse.success("删除成功", null);
    }

    /** 按教师ID级联删除所有教学班 */
    @DeleteMapping("/teacher/{teacherId}")
    public ApiResponse<Void> deleteByTeacherId(@PathVariable Long teacherId) {
        classInfoService.deleteClassesByTeacherId(teacherId);
        return ApiResponse.success("删除成功", null);
    }
}