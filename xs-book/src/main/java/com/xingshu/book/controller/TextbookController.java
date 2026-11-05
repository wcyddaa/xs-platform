package com.xingshu.book.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.book.model.dto.ChunkListResult;
import com.xingshu.book.model.dto.SearchRequest;
import com.xingshu.book.model.dto.SearchResult;
import com.xingshu.book.model.dto.StatsResponse;
import com.xingshu.book.model.dto.TextbookClassBindRequest;
import com.xingshu.book.model.dto.TextbookCreateRequest;
import com.xingshu.book.model.entity.TextbookEntity;
import com.xingshu.book.service.TextbookAsyncService;
import com.xingshu.book.service.FileStorageService;
import com.xingshu.book.service.PdfParsingService;
import com.xingshu.book.service.ProgressTracker;
import com.xingshu.book.service.TextbookService;
import com.xingshu.book.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/textbooks")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TextbookController {

    private static final Logger log = LoggerFactory.getLogger(TextbookController.class);

    private final TextbookService textbookService;
    private final VectorSearchService vectorSearchService;
    private final PdfParsingService pdfParsingService;
    private final FileStorageService fileStorageService;
    private final ProgressTracker progressTracker;
    private final TextbookAsyncService textbookAsyncService;

    public TextbookController(TextbookService textbookService,
                              VectorSearchService vectorSearchService,
                              PdfParsingService pdfParsingService,
                              FileStorageService fileStorageService,
                              ProgressTracker progressTracker,
                              TextbookAsyncService textbookAsyncService) {
        this.textbookService = textbookService;
        this.vectorSearchService = vectorSearchService;
        this.pdfParsingService = pdfParsingService;
        this.fileStorageService = fileStorageService;
        this.progressTracker = progressTracker;
        this.textbookAsyncService = textbookAsyncService;
    }

    // ==================== 教材列表 & 统计 ====================

    /**
     * 获取教材列表（分页 + 关键词搜索 + 分类筛选 + 排序）
     * GET /api/textbooks?keyword=xxx&category=xxx&page=1&size=10&sort=createTime&order=desc
     */
    @GetMapping
    public ApiResponse<PageResult<TextbookEntity>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createTime") String sort,
            @RequestParam(defaultValue = "desc") String order) {

        // 校验排序字段，防止恶意注入
        String allowedSort = switch (sort) {
            case "updateTime", "title" -> sort;
            default -> "createTime";
        };

        PageResult<TextbookEntity> result = textbookService.search(
                keyword, category, classId, page, size, allowedSort, order);
        return ApiResponse.success(result);
    }

    /**
     * 获取教材统计信息（首页 KPI 卡片用）
     * GET /api/textbooks/stats
     */
    @GetMapping("/stats")
    public ApiResponse<StatsResponse> stats() {
        StatsResponse stats = textbookService.getStats();
        return ApiResponse.success(stats);
    }

    /**
     * 获取教材目录摘要（供 AI 对话开始时了解教材大致内容）
     * GET /api/textbooks/{id}/outline
     */
    @GetMapping("/{id}/outline")
    public ApiResponse<Map<String, Object>> outline(@PathVariable String id) {
        return textbookService.findById(id)
                .map(entity -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", entity.getId());
                    data.put("title", entity.getTitle());
                    data.put("totalPages", entity.getTotalPages());
                    data.put("chapters", textbookService.extractOutline(id, 15));
                    return ApiResponse.success(data);
                })
                .orElseGet(() -> ApiResponse.error(404, "教材不存在"));
    }

    // ==================== 教材元数据 CRUD ====================

    /**
     * 创建教材（JSON 方式，含可选的切片）
     */
    @PostMapping
    public ApiResponse<TextbookEntity> create(@RequestBody TextbookCreateRequest request) {
        TextbookEntity entity = textbookService.create(request);

        if (request.getChunks() != null && !request.getChunks().isEmpty()) {
            for (TextbookCreateRequest.ChunkItem chunk : request.getChunks()) {
                vectorSearchService.addChunk(
                        entity.getId(), entity.getTitle(),
                        chunk.getChunkIndex(), chunk.getStartPage(),
                        chunk.getEndPage(),
                        chunk.getParagraphIndex() != null ? chunk.getParagraphIndex() : 0,
                        chunk.getContent()
                );
            }
        }

        return ApiResponse.success("教材创建成功", entity);
    }

    /** 支持的文件扩展名 */
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt"
    );

    /** 可通过 PDFBox 解析的文件类型 */
    private static final java.util.Set<String> PARSABLE_TYPES = java.util.Set.of("pdf");

    /**
     * 上传教材文件创建教材（支持 PDF/Word/PPT/Excel/TXT）
     * 
     * 上传采用异步处理：
     * 1. 立即保存教材元数据（状态 = processing）并返回
     * 2. 后台异步解析文件、生成向量并写入数据库
     * 3. 处理完成后更新状态为 completed（或 failed）
     */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "classId", required = false) Long classId,
            @RequestParam(value = "className", required = false) String className) throws IOException {

        if (file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }

        // 根据扩展名判断文件类型
        String originalName = file.getOriginalFilename();
        String fileType = "unknown";
        if (originalName != null) {
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileType = originalName.substring(dotIndex + 1).toLowerCase();
            }
        }

        if (!ALLOWED_EXTENSIONS.contains(fileType)) {
            return ApiResponse.error(400, "不支持的文件类型: ." + fileType + "，仅支持: pdf, doc, docx, ppt, pptx, xls, xlsx, txt");
        }

        // 创建教材元数据
        TextbookCreateRequest request = new TextbookCreateRequest();
        request.setTitle(title != null ? title : PdfParsingService.extractTitle(file));
        request.setCategory(category);
        request.setAuthor(author);
        request.setFileType(fileType);
        request.setFileSize(file.getSize());
        request.setClassId(classId);
        request.setClassName(className != null ? className : "");

        if (PARSABLE_TYPES.contains(fileType)) {
            // 可解析文件：先保存元数据，后台异步解析切片
            request.setTotalPages(pdfParsingService.getPageCount(file));
            TextbookEntity entity = textbookService.create(request);
            // 标记为处理中
            textbookService.updateStatus(entity.getId(), "processing");

            // 保存原始文件到云存储
            byte[] fileBytes = file.getBytes();
            String contentType = file.getContentType();
            try {
                fileStorageService.saveTextbook(entity.getId(), fileType, fileBytes);
            } catch (IOException e) {
                log.warn("保存教材文件到磁盘失败: {}", e.getMessage());
            }

            // 创建进度追踪
            String taskId = "upload_" + entity.getId();
            progressTracker.create(taskId, "upload", entity.getId(), entity.getTitle(),
                    entity.getTotalPages() != null ? entity.getTotalPages() : 0);

            textbookAsyncService.processFileAsync(entity.getId(), entity.getTitle(), fileBytes, originalName, contentType, taskId,
                    entity.getTotalPages() != null ? entity.getTotalPages() : 0);

            Map<String, Object> result = new HashMap<>();
            result.put("textbook", entity);
            result.put("taskId", taskId);
            return ApiResponse.success("教材上传成功，正在后台解析中...", result);
        } else {
            // 非 PDF 文件：仅存储元数据
            request.setTotalPages(0);
            TextbookEntity entity = textbookService.create(request);
            Map<String, Object> result = new HashMap<>();
            result.put("textbook", entity);
            return ApiResponse.success("上传成功（" + fileType.toUpperCase() + " 文件仅存储元数据）", result);
        }
    }

    /**
     * 绑定教材到教学班（classId 为 null 表示解绑）
     * PUT /api/textbooks/{id}/class
     * body: {"classId": 1, "className": "xx班"}
     */
    @PutMapping("/{id}/class")
    public ResponseEntity<ApiResponse<TextbookEntity>> bindClass(
            @PathVariable String id,
            @RequestBody TextbookClassBindRequest request) {
        return textbookService.bindClass(id, request.getClassId(), request.getClassName())
                .map(entity -> ResponseEntity.ok(ApiResponse.success(entity)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "教材不存在")));
    }

    /**
     * 解除教材与某个教学班的绑定（多对多，其它班级保留）
     * DELETE /api/textbooks/{id}/classes/{classId}
     */
    @DeleteMapping("/{id}/classes/{classId}")
    public ResponseEntity<ApiResponse<TextbookEntity>> unbindClass(
            @PathVariable String id,
            @PathVariable Long classId) {
        return textbookService.unbindClass(id, classId)
                .map(entity -> ResponseEntity.ok(ApiResponse.success(entity)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "教材不存在")));
    }

    /**
     * 修改教材名称
     * PUT /api/textbooks/{id}/title
     */
    @PutMapping("/{id}/title")
    public ResponseEntity<ApiResponse<TextbookEntity>> updateTitle(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "教材名称不能为空"));
        }
        return textbookService.updateTitle(id, title.trim())
                .map(entity -> ResponseEntity.ok(ApiResponse.success(entity)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "教材不存在")));
    }

    /**
     * 教学班删除时解除所有教材绑定（内部接口）
     * DELETE /api/textbooks/internal/class/{classId}
     */
    @DeleteMapping("/internal/class/{classId}")
    public ApiResponse<Integer> unbindAllForClass(@PathVariable Long classId) {
        return ApiResponse.success(textbookService.unbindAllForClass(classId));
    }

    /**
     * 根据ID查询教材
     */
    @GetMapping("/{id}")
    public ApiResponse<TextbookEntity> getById(@PathVariable String id) {
        return textbookService.findById(id)
                .map(entity -> ApiResponse.success(entity))
                .orElse(ApiResponse.error(404, "教材不存在"));
    }

    /**
     * 下载/预览教材文件
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getFile(@PathVariable String id) {
        Resource resource = fileStorageService.getTextbookFile(id);
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
     * 获取教材指定页的高清图片（PDF 图片流预览）
     * GET /api/textbooks/{id}/pages/{pageNum}?dpi=200
     * 
     * 将 PDF 每页渲染为 PNG 图片返回，前端通过图片流实现预览，
     * 从根本上杜绝用户直接下载或复制 PDF 文本内容。
     */
    @GetMapping("/{id}/pages/{pageNum}")
    public ResponseEntity<byte[]> getPageImage(
            @PathVariable String id,
            @PathVariable int pageNum,
            @RequestParam(defaultValue = "200") int dpi) {
        var entityOpt = textbookService.findById(id);
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
        // 只处理 pdf 文件
        Resource resource = fileStorageService.getTextbookFile(id);
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
            log.error("渲染教材页面失败: id={}, page={}", id, pageNum, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取教材页数信息（供前端预览组件初始化）
     * GET /api/textbooks/{id}/page-info
     */
    @GetMapping("/{id}/page-info")
    public ApiResponse<Map<String, Object>> getPageInfo(@PathVariable String id) {
        var entityOpt = textbookService.findById(id);
        if (entityOpt.isEmpty()) {
            return ApiResponse.error(404, "教材不存在");
        }
        var entity = entityOpt.get();
        Map<String, Object> info = new HashMap<>();
        info.put("totalPages", entity.getTotalPages());
        info.put("fileType", entity.getFileType());
        info.put("title", entity.getTitle());
        return ApiResponse.success(info);
    }

    /**
     * 删除教材（异步，返回 taskId 供前端轮询进度）
     * 进度显示：云端删除 + ES 删除，总步数 = 页数
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        var entityOpt = textbookService.findById(id);
        if (entityOpt.isEmpty()) {
            return ApiResponse.error(404, "教材不存在");
        }
        var entity = entityOpt.get();
        int totalPages = entity.getTotalPages() != null ? entity.getTotalPages() : 0;
        String taskId = "delete_" + id;
        progressTracker.create(taskId, "delete", id, entity.getTitle(), totalPages);

        // 异步执行删除操作
        textbookAsyncService.processDeleteAsync(id, entity.getTitle(), totalPages, taskId);

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("textbookId", id);
        result.put("title", entity.getTitle());
        return ApiResponse.success("教材删除已启动，正在后台处理...", result);
    }

    /**
     * 删除所有教材和切片数据（用于清空旧数据）
     * DELETE /api/textbooks/all/confirm?yes=1
     * 
     * 使用多段路径避免与 DELETE /{id} 路由冲突
     */
    @DeleteMapping("/all/confirm")
    public ApiResponse<Map<String, Object>> deleteAll(@RequestParam(defaultValue = "no") String yes) {
        if (!"yes".equals(yes) && !"1".equals(yes)) {
            return ApiResponse.error(400, "请添加 ?yes=1 参数确认删除所有教材数据");
        }
        long count = textbookService.deleteAll();
        long chunkRemaining = textbookService.countChunks();

        // 如果仍有残留切片，用 force 方式再清一次
        if (chunkRemaining > 0) {
            log.warn("首次清空后仍有 {} 个切片残留，执行二次强制清理", chunkRemaining);
            long forceDeleted = vectorSearchService.forceDeleteAllChunks();
            chunkRemaining = textbookService.countChunks();
            log.info("二次强制清理完成，删除 {} 个切片，剩余 {} 个", forceDeleted, chunkRemaining);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deletedTextbooks", count);
        result.put("chunkRemaining", chunkRemaining);
        if (chunkRemaining > 0) {
            return ApiResponse.success("已删除 " + count + " 条教材，但仍有 " + chunkRemaining + " 个切片残留，请再次执行", result);
        }
        return ApiResponse.success("已清空所有教材数据，共删除 " + count + " 条教材", result);
    }

    /**
     * 查看切片残留数量
     * GET /api/textbooks/chunks/count
     */
    @GetMapping("/chunks/count")
    public ApiResponse<Long> countChunks() {
        long count = textbookService.countChunks();
        return ApiResponse.success(count);
    }

    /**
     * 强制清空所有切片（无视教材关联）
     * DELETE /api/textbooks/chunks/all?yes=1
     */
    @DeleteMapping("/chunks/all")
    public ApiResponse<Long> forceDeleteChunks(@RequestParam(defaultValue = "no") String yes) {
        if (!"yes".equals(yes) && !"1".equals(yes)) {
            return ApiResponse.error(400, "请添加 ?yes=1 参数确认删除所有切片");
        }
        long deleted = vectorSearchService.forceDeleteAllChunks();
        long remaining = textbookService.countChunks();
        return ApiResponse.success("强制清空完成，删除 " + deleted + " 个切片，剩余 " + remaining + " 个", deleted);
    }

    // ==================== 切片管理 ====================

    /**
     * 获取指定教材的切片列表（分页）
     * GET /api/textbooks/{textbookId}/chunks?keyword=xxx&page=1&size=10
     */
    @GetMapping("/{textbookId}/chunks")
    public ApiResponse<PageResult<ChunkListResult>> listChunks(
            @PathVariable String textbookId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        // 先检查教材是否存在
        if (textbookService.findById(textbookId).isEmpty()) {
            return ApiResponse.error(404, "教材不存在");
        }

        PageResult<ChunkListResult> result = vectorSearchService.listChunks(
                textbookId, keyword, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 为指定教材添加切片
     */
    @PostMapping("/{textbookId}/chunks")
    public ApiResponse<String> addChunk(
            @PathVariable String textbookId,
            @RequestBody Map<String, Object> chunkData) {

        var entity = textbookService.findById(textbookId);
        if (entity.isEmpty()) {
            return ApiResponse.error(404, "教材不存在");
        }

        String chunkId = vectorSearchService.addChunk(
                textbookId,
                entity.get().getTitle(),
                (Integer) chunkData.get("chunkIndex"),
                (Integer) chunkData.get("startPage"),
                (Integer) chunkData.get("endPage"),
                chunkData.get("paragraphIndex") != null ? (Integer) chunkData.get("paragraphIndex") : 0,
                (String) chunkData.get("content")
        );
        return ApiResponse.success("切片添加成功", chunkId);
    }

    /**
     * 批量添加切片
     */
    @PostMapping("/{textbookId}/chunks/batch")
    public ApiResponse<Void> addChunks(
            @PathVariable String textbookId,
            @RequestBody List<Map<String, Object>> chunks) {

        var entity = textbookService.findById(textbookId);
        if (entity.isEmpty()) {
            return ApiResponse.error(404, "教材不存在");
        }

        List<Document> documents = chunks.stream()
                .map(chunk -> {
                    String chunkId = java.util.UUID.randomUUID().toString().replace("-", "");
                    Map<String, Object> metadata = Map.of(
                            "chunkId", chunkId,
                            "textbookId", textbookId,
                            "textbookTitle", entity.get().getTitle(),
                            "chunkIndex", chunk.getOrDefault("chunkIndex", 0),
                            "startPage", chunk.getOrDefault("startPage", 0),
                            "endPage", chunk.getOrDefault("endPage", 0),
                            "paragraphIndex", chunk.getOrDefault("paragraphIndex", 0)
                    );
                    return new Document(chunkId, (String) chunk.get("content"), metadata);
                })
                .toList();

        vectorSearchService.addChunks(documents);
        return ApiResponse.success("批量添加成功", null);
    }

    /**
     * 删除指定切片
     * DELETE /api/textbooks/{textbookId}/chunks/{chunkId}
     */
    @DeleteMapping("/{textbookId}/chunks/{chunkId}")
    public ApiResponse<Void> deleteChunk(
            @PathVariable String textbookId,
            @PathVariable String chunkId) {

        // 检查教材是否存在
        if (textbookService.findById(textbookId).isEmpty()) {
            return ApiResponse.error(404, "教材不存在");
        }

        vectorSearchService.deleteChunk(chunkId);
        return ApiResponse.success("切片删除成功", null);
    }

    // ==================== 进度查询 ====================

    /**
     * 查询教材上传/删除进度
     * GET /api/textbooks/progress/{taskId}
     */
    @GetMapping("/progress/{taskId}")
    public ApiResponse<ProgressTracker.TaskProgress> getProgress(@PathVariable String taskId) {
        ProgressTracker.TaskProgress progress = progressTracker.get(taskId);
        if (progress == null) {
            return ApiResponse.error(404, "任务不存在或已过期");
        }
        return ApiResponse.success(progress);
    }

    // ==================== 语义搜索 ====================

    /**
     * 语义搜索（核心接口，供 FastAPI 智能体调用）
     * 返回的是文本内容 + 元数据，不返回向量
     */
    @PostMapping("/search")
    public ApiResponse<List<SearchResult>> search(@RequestBody SearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return ApiResponse.error(400, "搜索关键词不能为空");
        }
        List<SearchResult> results = vectorSearchService.search(request);
        return ApiResponse.success(results);
    }
}