package com.xingshu.book.controller;

import com.xingshu.book.model.dto.CoursewareFromTextbookRequest;
import com.xingshu.book.model.entity.CoursewareEntity;
import com.xingshu.book.model.entity.TextbookEntity;
import com.xingshu.book.service.CoursewareService;
import com.xingshu.book.service.FileStorageService;
import com.xingshu.book.service.PdfParsingService;
import com.xingshu.book.service.TextbookService;
import com.xingshu.book.model.dto.SearchRequest;
import com.xingshu.book.model.dto.SearchResult;
import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/courseware")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CoursewareController {

    private static final Logger log = LoggerFactory.getLogger(CoursewareController.class);

    private final CoursewareService coursewareService;
    private final PdfParsingService pdfParsingService;
    private final FileStorageService fileStorageService;
    private final TextbookService textbookService;

    public CoursewareController(CoursewareService coursewareService,
                                PdfParsingService pdfParsingService,
                                FileStorageService fileStorageService,
                                TextbookService textbookService) {
        this.coursewareService = coursewareService;
        this.pdfParsingService = pdfParsingService;
        this.fileStorageService = fileStorageService;
        this.textbookService = textbookService;
    }

    // ==================== 课件CRUD ====================

    /** 分页查询某课程的课件列表（可按章节过滤） */
    @GetMapping
    public ApiResponse<PageResult<CoursewareEntity>> list(
            @RequestParam Integer courseId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<CoursewareEntity> result = coursewareService.listByCourse(courseId, chapterId, page, size);
        return ApiResponse.success(result);
    }

    /** 上传课件文件（每页一个切片，关联课程与章节） */
    @PostMapping("/upload")
    public ApiResponse<CoursewareEntity> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("courseId") Integer courseId,
            @RequestParam("courseName") String courseName,
            @RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("createrId") Integer createrId,
            @RequestParam("createrName") String createrName) throws IOException {

        if (file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String fileType = "unknown";
        if (originalName != null) {
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileType = originalName.substring(dotIndex + 1).toLowerCase();
            }
        }

        // 创建课件元数据
        CoursewareEntity entity = new CoursewareEntity();
        entity.setCourseId(courseId);
        entity.setCourseName(courseName);
        entity.setChapterId(chapterId);
        entity.setTitle(title != null ? title : PdfParsingService.extractTitle(file));
        entity.setFileType(fileType);
        entity.setFileSize(file.getSize());

        // 只有PDF文件才计算页数，非PDF文件默认1页
        boolean isPdf = "pdf".equals(fileType);
        int totalPages = 1;
        if (isPdf) {
            try {
                totalPages = pdfParsingService.getPageCount(file);
            } catch (Exception e) {
                log.warn("获取PDF页数失败: {}", e.getMessage());
                totalPages = 1;
            }
        }
        entity.setTotalPages(totalPages);
        entity.setCreaterId(createrId);
        entity.setCreaterName(createrName);

        CoursewareEntity saved = coursewareService.create(entity);

        // 异步处理：仅PDF文件做逐页切片，非PDF文件直接标记为完成
        byte[] fileBytes = file.getBytes();
        String contentType = file.getContentType();

        // 保存原始文件到磁盘
        try {
            fileStorageService.saveCourseware(saved.getId(), fileType, fileBytes);
        } catch (IOException e) {
            log.warn("保存课件文件到磁盘失败: {}", e.getMessage());
        }

        if (isPdf) {
            processCoursewareAsync(saved.getId(), saved.getCourseId(), saved.getTitle(),
                    fileBytes, originalName, contentType, saved.getTotalPages());
        } else {
            coursewareService.updateStatus(saved.getId(), "completed");
            log.info("非PDF课件上传完成，跳过切片处理: id={}", saved.getId());
        }

        return ApiResponse.success("课件上传成功，正在后台解析中...", saved);
    }

    /**
     * 由教材指定页范围生成课件（截取教材 PDF 的第 startPage-endPage 页）
     * POST /api/courseware/from-textbook
     * body: {"textbookId":"...","startPage":3,"endPage":5,"title":"可选",
     *        "courseId":1,"courseName":"...","chapterId":10,"createrId":1,"createrName":"..."}
     */
    @PostMapping("/from-textbook")
    public ApiResponse<CoursewareEntity> createFromTextbook(
            @RequestBody CoursewareFromTextbookRequest request) throws IOException {

        if (request.getTextbookId() == null || request.getTextbookId().isBlank()) {
            return ApiResponse.error(400, "textbookId 不能为空");
        }

        var textbookOpt = textbookService.findById(request.getTextbookId());
        if (textbookOpt.isEmpty()) {
            return ApiResponse.error(404, "教材不存在");
        }
        TextbookEntity textbook = textbookOpt.get();

        if (!"pdf".equalsIgnoreCase(textbook.getFileType())) {
            return ApiResponse.error(400, "该教材不是 PDF 文件，无法按页截取");
        }
        Integer totalPages = textbook.getTotalPages();
        if (totalPages == null || totalPages <= 0) {
            return ApiResponse.error(400, "该教材页数未知，无法按页截取");
        }

        Integer startPageReq = request.getStartPage();
        Integer endPageReq = request.getEndPage();
        if (startPageReq == null || endPageReq == null) {
            return ApiResponse.error(400, "startPage 和 endPage 不能为空");
        }
        int startPage = startPageReq;
        int endPage = endPageReq;
        if (startPage < 1 || startPage > endPage || endPage > totalPages) {
            return ApiResponse.error(400,
                    "页码范围不合法，需满足 1 <= startPage <= endPage <= " + totalPages);
        }

        // 读取教材原始 PDF 并抽取指定页范围
        byte[] sourceBytes = fileStorageService.readTextbookBytes(textbook.getId());
        if (sourceBytes == null || sourceBytes.length == 0) {
            return ApiResponse.error(404, "教材原文件不存在，无法截取页面");
        }
        byte[] pdfBytes = pdfParsingService.extractPageRange(sourceBytes, startPage, endPage);

        // 创建课件元数据（与上传链路一致）
        CoursewareEntity entity = new CoursewareEntity();
        entity.setCourseId(request.getCourseId());
        entity.setCourseName(request.getCourseName());
        entity.setChapterId(request.getChapterId());
        String defaultTitle = textbook.getTitle() + " · 第" + startPage + "-" + endPage + "页";
        entity.setTitle(request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle() : defaultTitle);
        entity.setFileType("pdf");
        entity.setFileSize((long) pdfBytes.length);
        entity.setTotalPages(endPage - startPage + 1);
        entity.setCreaterId(request.getCreaterId());
        entity.setCreaterName(request.getCreaterName());

        CoursewareEntity saved = coursewareService.create(entity);

        // 保存新生成的 PDF 课件文件
        try {
            fileStorageService.saveCourseware(saved.getId(), "pdf", pdfBytes);
        } catch (IOException e) {
            log.warn("保存课件文件到磁盘失败: {}", e.getMessage());
        }

        // 走与课件上传一致的异步逐页切片链路
        processCoursewareAsync(saved.getId(), saved.getCourseId(), saved.getTitle(),
                pdfBytes, saved.getTitle() + ".pdf", MediaType.APPLICATION_PDF_VALUE, saved.getTotalPages());

        return ApiResponse.success("课件生成成功，正在后台解析中...", saved);
    }

    /** 异步处理课件：每页一个切片 */
    @Async
    public CompletableFuture<Void> processCoursewareAsync(String coursewareId, Integer courseId,
                                                          String coursewareTitle, byte[] fileBytes,
                                                          String originalName, String contentType,
                                                          int totalPages) {
        try {
            log.info("开始异步处理课件: id={}, name={}, pages={}", coursewareId, originalName, totalPages);
            MultipartFile file = new MultipartFile() {
                @Override public String getName() { return "file"; }
                @Override public String getOriginalFilename() { return originalName; }
                @Override public String getContentType() { return contentType; }
                @Override public boolean isEmpty() { return fileBytes.length == 0; }
                @Override public long getSize() { return fileBytes.length; }
                @Override public byte[] getBytes() { return fileBytes; }
                @Override public InputStream getInputStream() { return new ByteArrayInputStream(fileBytes); }
                @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), fileBytes); }
            };

            // 每页一个切片
            List<PdfParsingService.PageContent> pages = pdfParsingService.extractPageContents(file);
            for (int i = 0; i < pages.size(); i++) {
                PdfParsingService.PageContent pc = pages.get(i);
                if (pc.content() != null && !pc.content().isBlank()) {
                    coursewareService.addChunk(coursewareId, courseId, coursewareTitle,
                            pc.pageNumber(), pc.content());
                }
            }
            coursewareService.updateStatus(coursewareId, "completed");
            log.info("课件异步处理完成: id={}, 切片数={}", coursewareId, pages.size());
        } catch (Exception e) {
            log.error("课件异步处理失败: id={}", coursewareId, e);
            coursewareService.updateStatus(coursewareId, "failed");
        }
        return CompletableFuture.completedFuture(null);
    }

    /** 获取课件详情 */
    @GetMapping("/{id}")
    public ApiResponse<CoursewareEntity> getById(@PathVariable String id) {
        return coursewareService.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "课件不存在"));
    }

    /** 下载/预览课件文件 */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getFile(@PathVariable String id) {
        Resource resource = fileStorageService.getCoursewareFile(id);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String filename = resource.getFilename();
        MediaType mediaType = MediaType.APPLICATION_PDF;
        if (filename != null) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            mediaType = switch (ext) {
                case "pdf" -> MediaType.APPLICATION_PDF;
                case "doc", "docx" -> MediaType.parseMediaType("application/msword");
                case "ppt", "pptx" -> MediaType.parseMediaType("application/vnd.ms-powerpoint");
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    /**
     * 获取课件指定页的高清图片（PDF 图片流预览）
     * GET /api/courseware/{id}/pages/{pageNum}?dpi=200
     */
    @GetMapping("/{id}/pages/{pageNum}")
    public ResponseEntity<byte[]> getPageImage(
            @PathVariable String id,
            @PathVariable int pageNum,
            @RequestParam(defaultValue = "200") int dpi) {
        var entityOpt = coursewareService.findById(id);
        if (entityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var entity = entityOpt.get();
        if (!"pdf".equalsIgnoreCase(entity.getFileType())) {
            return ResponseEntity.badRequest().build();
        }
        if (pageNum < 1 || pageNum > entity.getTotalPages()) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = fileStorageService.getCoursewareFile(id);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] pdfBytes = resource.getInputStream().readAllBytes();
            byte[] imageBytes = pdfParsingService.renderPageAsImage(pdfBytes, pageNum, dpi);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(imageBytes);
        } catch (Exception e) {
            log.error("渲染课件页面失败: id={}, page={}", id, pageNum, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取课件页数信息（供前端预览组件初始化）
     * GET /api/courseware/{id}/page-info
     */
    @GetMapping("/{id}/page-info")
    public ApiResponse<Map<String, Object>> getPageInfo(@PathVariable String id) {
        var entityOpt = coursewareService.findById(id);
        if (entityOpt.isEmpty()) {
            return ApiResponse.error(404, "课件不存在");
        }
        var entity = entityOpt.get();
        Map<String, Object> info = new HashMap<>();
        info.put("totalPages", entity.getTotalPages());
        info.put("fileType", entity.getFileType());
        info.put("title", entity.getTitle());
        return ApiResponse.success(info);
    }

    /** 删除课件及其切片、文件 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        if (coursewareService.findById(id).isEmpty()) {
            return ApiResponse.error(404, "课件不存在");
        }
        coursewareService.deleteById(id);
        return ApiResponse.success("课件删除成功", null);
    }

    /** 课程删除时级联清理课件（内部接口） */
    @DeleteMapping("/internal/by-course/{courseId}")
    public ApiResponse<Integer> deleteByCourse(@PathVariable Integer courseId) {
        return ApiResponse.success(coursewareService.deleteByCourse(courseId));
    }

    /** 章节删除时级联清理课件（内部接口） */
    @DeleteMapping("/internal/by-chapter/{chapterId}")
    public ApiResponse<Integer> deleteByChapter(@PathVariable Long chapterId) {
        return ApiResponse.success(coursewareService.deleteByChapter(chapterId));
    }

    // ==================== 课件语义搜索 ====================

    /** 语义搜索课件内容（供AI参考） */
    @PostMapping("/search")
    public ApiResponse<List<SearchResult>> search(@RequestBody SearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return ApiResponse.error(400, "搜索关键词不能为空");
        }
        List<SearchResult> results = coursewareService.search(request);
        return ApiResponse.success(results);
    }
}