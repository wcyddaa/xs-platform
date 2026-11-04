package com.xingshu.exam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.common.constant.CommonConstants;
import com.xingshu.exam.domain.po.ClassHomework;
import com.xingshu.exam.domain.po.StudentHomework;
import com.xingshu.exam.domain.po.TemplateHomework;
import com.xingshu.exam.service.HomeworkFileService;
import com.xingshu.exam.service.IClassHomeworkService;
import com.xingshu.exam.service.IStudentHomeworkService;
import com.xingshu.exam.service.ITemplateHomeworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/homework")
public class HomeworkController {

    private static final Logger log = LoggerFactory.getLogger(HomeworkController.class);

    private final ITemplateHomeworkService templateHomeworkService;
    private final IStudentHomeworkService studentHomeworkService;
    private final IClassHomeworkService classHomeworkService;
    private final HomeworkFileService homeworkFileService;

    public HomeworkController(ITemplateHomeworkService templateHomeworkService,
                              IStudentHomeworkService studentHomeworkService,
                              IClassHomeworkService classHomeworkService,
                              HomeworkFileService homeworkFileService) {
        this.templateHomeworkService = templateHomeworkService;
        this.studentHomeworkService = studentHomeworkService;
        this.classHomeworkService = classHomeworkService;
        this.homeworkFileService = homeworkFileService;
    }

    // ==================== 模板作业 ====================

    /** 创建/保存模板作业 */
    @PostMapping("/templates")
    public ApiResponse<TemplateHomework> createTemplate(@RequestBody TemplateHomework template) {
        if (template.getStatus() == null) {
            template.setStatus(CommonConstants.TEMPLATE_STATUS_DRAFT);
        }

        // 检查附件 downloadUrl 中是否引用了 homeworkId=0（新创建时前端上传用的临时ID）
        String attachments = template.getAttachments();
        boolean hasMigration = attachments != null && attachments.contains("/attachments/0/");

        templateHomeworkService.save(template);

        // 迁移附件：从 temp 目录(ID=0) 迁移到真实 ID 目录
        if (hasMigration && template.getId() != null) {
            try {
                homeworkFileService.migrateAttachments(0L, template.getId());
                // 更新附件中的 downloadUrl
                String updatedAttachments = attachments.replace("/attachments/0/", "/attachments/" + template.getId() + "/");
                TemplateHomework update = new TemplateHomework();
                update.setId(template.getId());
                update.setAttachments(updatedAttachments);
                templateHomeworkService.updateById(update);
                template.setAttachments(updatedAttachments);
            } catch (Exception e) {
                log.warn("迁移作业附件失败: templateId={}, error={}", template.getId(), e.getMessage());
            }
        }

        return ApiResponse.success("保存成功", template);
    }

    /** 更新模板作业 */
    @PutMapping("/templates/{id}")
    public ApiResponse<Void> updateTemplate(@PathVariable Long id, @RequestBody TemplateHomework template) {
        template.setId(id);
        templateHomeworkService.updateById(template);
        return ApiResponse.success("更新成功", null);
    }

    /** 发布模板作业 */
    @PutMapping("/templates/{id}/publish")
    public ApiResponse<Void> publishTemplate(@PathVariable Long id) {
        templateHomeworkService.publish(id);
        return ApiResponse.success("发布成功", null);
    }

    /** 删除模板作业（级联删除实例作业和附件） */
    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        // 1. 删除附件文件
        homeworkFileService.deleteAttachments(id);
        // 2. 删除关联的实例作业
        studentHomeworkService.deleteByTemplateIds(List.of(id));
        // 3. 删除班级-作业关联
        classHomeworkService.deleteByTemplateIds(List.of(id));
        // 4. 删除模板作业
        templateHomeworkService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /** 教师分页查询自己的模板作业 */
    @GetMapping("/templates/teacher")
    public ApiResponse<PageResult<TemplateHomework>> listByTeacher(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam Long teacherId,
            @RequestParam(required = false) String classIds) {
        List<Long> cids = null;
        if (classIds != null && !classIds.isEmpty()) {
            cids = List.of(classIds.split(",")).stream().map(Long::valueOf).collect(Collectors.toList());
        }
        Page<TemplateHomework> page = templateHomeworkService.pageByTeacher(pageNum, pageSize, teacherId, cids);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 学生查询自己教学班的已发布模板作业 */
    @GetMapping("/templates/student")
    public ApiResponse<PageResult<TemplateHomework>> listByStudent(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam String classIds) {
        List<Long> cids = List.of(classIds.split(",")).stream().map(Long::valueOf).collect(Collectors.toList());
        Page<TemplateHomework> page = templateHomeworkService.pageByStudentClasses(pageNum, pageSize, cids);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 获取单个模板作业 */
    @GetMapping("/templates/{id}")
    public ApiResponse<TemplateHomework> getTemplate(@PathVariable Long id) {
        return ApiResponse.success(templateHomeworkService.getById(id));
    }

    /** 教师发布作业数统计 */
    @GetMapping("/templates/teacher/stats")
    public ApiResponse<java.util.Map<String, Long>> teacherStats(@RequestParam Long teacherId) {
        long count = templateHomeworkService.countPublishedByTeacher(teacherId);
        return ApiResponse.success(java.util.Map.of("publishedCount", count));
    }

    // ==================== 学生实例作业 ====================

    /** 学生提交作业（创建实例） */
    @PostMapping("/students/submit")
    public ApiResponse<Void> submit(@RequestBody Map<String, Object> body) {
        Long templateId = Long.valueOf(body.get("templateId").toString());
        Long studentId = Long.valueOf(body.get("studentId").toString());
        String studentName = (String) body.getOrDefault("studentName", "");
        String answer = (String) body.getOrDefault("answer", "");
        studentHomeworkService.submitHomework(templateId, studentId, studentName, answer);
        return ApiResponse.success("提交成功", null);
    }

    /** 获取模板作业的所有学生提交 */
    @GetMapping("/templates/{templateId}/submissions")
    public ApiResponse<List<StudentHomework>> getSubmissions(@PathVariable Long templateId) {
        return ApiResponse.success(studentHomeworkService.getByTemplateId(templateId));
    }

    /** 分页查询学生实例作业 */
    @GetMapping("/students")
    public ApiResponse<PageResult<StudentHomework>> listStudentHomework(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long templateId) {
        Page<StudentHomework> page = studentHomeworkService.pageByStudent(pageNum, pageSize, studentId, templateId);
        return ApiResponse.success(PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** 老师批改作业 */
    @PutMapping("/students/{id}/grade")
    public ApiResponse<Void> grade(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Double score = body.get("score") != null ? Double.valueOf(body.get("score").toString()) : null;
        String comment = (String) body.getOrDefault("comment", "");
        studentHomeworkService.gradeHomework(id, score, comment);
        return ApiResponse.success("批改成功", null);
    }

    // ==================== 班级作业关联 ====================

    @GetMapping("/classes/{classId}")
    public ApiResponse<List<ClassHomework>> getClassHomeworks(@PathVariable Long classId) {
        return ApiResponse.success(classHomeworkService.getByClassId(classId));
    }

    @GetMapping("/templates/{templateId}/classes")
    public ApiResponse<List<ClassHomework>> getTemplateClasses(@PathVariable Long templateId) {
        return ApiResponse.success(classHomeworkService.getByTemplateId(templateId));
    }

    @PostMapping("/classes/bind")
    public ApiResponse<Void> bindClass(@RequestBody Map<String, Object> body) {
        Long classId = Long.valueOf(body.get("classId").toString());
        String className = (String) body.getOrDefault("className", "");
        Long templateId = Long.valueOf(body.get("templateId").toString());
        classHomeworkService.bind(classId, className, templateId);
        return ApiResponse.success("绑定成功", null);
    }

    @DeleteMapping("/classes/unbind")
    public ApiResponse<Void> unbindClass(@RequestParam Long classId,
                                          @RequestParam Long templateId) {
        classHomeworkService.unbind(classId, templateId);
        return ApiResponse.success("解绑成功", null);
    }

    // ==================== 级联删除（供其他服务调用） ====================

    /** 按教学班删除模板作业 */
    @DeleteMapping("/templates/class/{classId}")
    public ApiResponse<Void> deleteByClassId(@PathVariable Long classId) {
        templateHomeworkService.deleteByClassId(classId);
        return ApiResponse.success("删除成功", null);
    }

    /** 按课程删除模板作业 */
    @DeleteMapping("/templates/course/{courseId}")
    public ApiResponse<Void> deleteByCourseId(@PathVariable Long courseId) {
        templateHomeworkService.deleteByCourseId(courseId);
        return ApiResponse.success("删除成功", null);
    }

    /** 按学生ID删除作业提交 */
    @DeleteMapping("/students/student/{studentId}")
    public ApiResponse<Void> deleteByStudentId(@PathVariable Long studentId) {
        studentHomeworkService.deleteByStudentId(studentId);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 作业附件 ====================

    /**
     * 上传作业附件
     * 
     * @param homeworkId 作业模板 ID（新建作业时传 0，后端返回临时 ID 供前端暂存）
     */
    @PostMapping("/attachments/upload")
    public ApiResponse<Map<String, String>> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "homeworkId", defaultValue = "0") Long homeworkId) throws IOException {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        // 保存文件
        Path savedPath = homeworkFileService.saveAttachment(homeworkId, originalName, file.getBytes());

        // 返回文件信息（前端存到 attachments JSON 中）
        String downloadUrl = "/api/homework/attachments/" + homeworkId + "/"
                + URLEncoder.encode(originalName, StandardCharsets.UTF_8);
        return ApiResponse.success("上传成功", Map.of(
                "fileName", originalName,
                "fileType", file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                "downloadUrl", downloadUrl
        ));
    }

    /**
     * 下载作业附件
     */
    @GetMapping("/attachments/{homeworkId}/{fileName}")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long homeworkId,
            @PathVariable String fileName) {
        Resource resource = homeworkFileService.getAttachment(homeworkId, fileName);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = "application/octet-stream";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (lowerName.endsWith(".png")) {
            contentType = "image/png";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) {
            contentType = "application/msword";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                .body(resource);
    }

    /**
     * 删除作业的所有附件
     */
    @DeleteMapping("/attachments/{homeworkId}")
    public ApiResponse<Void> deleteAttachments(@PathVariable Long homeworkId) {
        homeworkFileService.deleteAttachments(homeworkId);
        return ApiResponse.success("删除成功", null);
    }
}