package com.xingshu.course.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.course.domain.po.Chapter;
import com.xingshu.course.domain.po.Course;
import com.xingshu.course.domain.po.CourseClass;
import com.xingshu.course.service.IChapterService;
import com.xingshu.course.service.ICourseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final ICourseService courseService;
    private final IChapterService chapterService;

    public CourseController(ICourseService courseService, IChapterService chapterService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
    }

    /**
     * 分页查询课程
     */
    @GetMapping
    public ApiResponse<PageResult<Course>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String keyword) {
        Page<Course> page = courseService.pageCourses(pageNum, pageSize, keyword);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 获取创建者的课程列表（教师端"我的课程"）
     */
    @GetMapping("/creator/{creatorId}")
    public ApiResponse<PageResult<Course>> listByCreator(@PathVariable Long creatorId,
                                                    @RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "100") Integer pageSize) {
        Page<Course> page = courseService.pageByCreator(pageNum, pageSize, creatorId);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 创建课程
     */
    @PostMapping
    public ApiResponse<Course> create(@RequestBody Map<String, Object> body) {
        Course course = new Course();
        course.setName((String) body.get("name"));
        course.setDescription((String) body.getOrDefault("description", ""));
        course.setTextbookId((String) body.getOrDefault("textbookId", ""));
        course.setTextbookTitle((String) body.getOrDefault("textbookTitle", ""));
        // 默认草稿状态
        course.setStatus((String) body.getOrDefault("status", "draft"));
        if (body.get("createrId") != null) {
            course.setCreaterId(Long.valueOf(body.get("createrId").toString()));
        }
        courseService.save(course);
        return ApiResponse.success("课程创建成功", course);
    }

    /**
     * 更新课程
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody Course course) {
        course.setId(id);
        courseService.updateById(course);
        return ApiResponse.success("更新成功", null);
    }

    /**
     * 删除课程（仅当所有关联数据已清空时才允许）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            courseService.deleteCourse(id);
            return ApiResponse.success("删除成功", null);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除前预检查：返回课程绑定的数据概览
     */
    @GetMapping("/{id}/pre-check")
    public ApiResponse<Map<String, Object>> preCheckDelete(@PathVariable Long id) {
        return ApiResponse.success(courseService.preCheckDelete(id));
    }

    /**
     * 审核通过课程
     */
    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        courseService.approveCourse(id);
        return ApiResponse.success("审核通过", null);
    }

    // ==================== 课程-教学班关联 ====================

    /**
     * 获取课程的教学班列表
     */
    @GetMapping("/{courseId}/classes")
    public ApiResponse<List<CourseClass>> getClasses(@PathVariable Long courseId) {
        return ApiResponse.success(courseService.getClasses(courseId));
    }

    /**
     * 绑定教学班到课程
     */
    @PostMapping("/{courseId}/classes")
    public ApiResponse<Void> addClass(@PathVariable Long courseId,
                                       @RequestBody Map<String, Object> body) {
        Long classId = Long.valueOf(body.get("classId").toString());
        String className = (String) body.getOrDefault("className", "");
        courseService.addClass(courseId, classId, className);
        return ApiResponse.success("添加成功", null);
    }

    /**
     * 移除教学班
     */
    @DeleteMapping("/{courseId}/classes/{classId}")
    public ApiResponse<Void> removeClass(@PathVariable Long courseId,
                                          @PathVariable Long classId) {
        courseService.removeClass(courseId, classId);
        return ApiResponse.success("移除成功", null);
    }

    // ==================== 章节管理 ====================

    /**
     * 获取课程章节列表
     */
    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<Chapter>> getChapters(@PathVariable Long courseId) {
        return ApiResponse.success(chapterService.getByCourseId(courseId));
    }

    /**
     * 批量添加章节
     */
    @PostMapping("/{courseId}/chapters")
    public ApiResponse<Void> addChapters(@PathVariable Long courseId,
                                          @RequestBody List<Chapter> chapters) {
        chapterService.batchCreate(courseId, chapters);
        return ApiResponse.success("添加成功", null);
    }

    /**
     * 更新章节
     */
    @PutMapping("/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> updateChapter(@PathVariable Long courseId,
                                            @PathVariable Long chapterId,
                                            @RequestBody Chapter chapter) {
        chapter.setId(chapterId);
        chapter.setCourseId(courseId);
        chapterService.updateById(chapter);
        return ApiResponse.success("更新成功", null);
    }

    /**
     * 删除章节
     */
    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@PathVariable Long courseId,
                                            @PathVariable Long chapterId) {
        chapterService.removeById(chapterId);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.success(Map.of("courseCount", courseService.count()));
    }

    // ==================== 级联删除（供其他服务调用） ====================

    /** 按创建者ID级联删除所有课程 */
    @DeleteMapping("/creator/{creatorId}")
    public ApiResponse<Void> deleteByCreatorId(@PathVariable Long creatorId) {
        courseService.deleteCoursesByCreatorId(creatorId);
        return ApiResponse.success("删除成功", null);
    }

    /** 清除指定教材ID的课程绑定（教材删除时调用） */
    @PutMapping("/textbook/{textbookId}/clear")
    public ApiResponse<Void> clearTextbookBinding(@PathVariable String textbookId) {
        courseService.clearTextbookBinding(textbookId);
        return ApiResponse.success("清除成功", null);
    }
}