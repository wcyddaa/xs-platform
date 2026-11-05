package com.xingshu.book.service;

import com.xingshu.book.service.PdfParsingService.ChunkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 教材异步处理服务
 * <p>
 * 两阶段进度：cloud（云端存储）→ database（数据库向量化）。
 * 将 @Async 方法从 TextbookController 中提取到独立 Service，
 * 确保 Spring AOP 代理能正确拦截（避免 self-invocation 导致 @Async 失效）。
 */
@Service
public class TextbookAsyncService {

    private static final Logger log = LoggerFactory.getLogger(TextbookAsyncService.class);

    private final TextbookService textbookService;
    private final VectorSearchService vectorSearchService;
    private final PdfParsingService pdfParsingService;
    private final FileStorageService fileStorageService;
    private final ProgressTracker progressTracker;

    public TextbookAsyncService(TextbookService textbookService,
                                VectorSearchService vectorSearchService,
                                PdfParsingService pdfParsingService,
                                FileStorageService fileStorageService,
                                ProgressTracker progressTracker) {
        this.textbookService = textbookService;
        this.vectorSearchService = vectorSearchService;
        this.pdfParsingService = pdfParsingService;
        this.fileStorageService = fileStorageService;
        this.progressTracker = progressTracker;
    }

    /**
     * 异步处理教材文件（两阶段：云端存储 → 数据库向量化）
     */
    @Async
    public CompletableFuture<Void> processFileAsync(String textbookId, String textbookTitle,
                                                     byte[] fileBytes, String originalName, String contentType,
                                                     String taskId, int totalPages) {
        try {
            log.info("开始异步处理教材: id={}, name={}, pages={}", textbookId, originalName, totalPages);

            // ===== 阶段1：云端存储 =====
            progressTracker.update(taskId, 0, "正在上传云端存储...");
            // 文件已在 Controller 中同步上传到云端，此处标记完成
            progressTracker.update(taskId, totalPages, "云端存储上传完成");

            // ===== 解析 PDF，计算切片总数 =====
            progressTracker.update(taskId, totalPages, "正在解析 PDF，计算切片总数...");

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

            List<ChunkResult> chunks = pdfParsingService.chunkByPageAndParagraph(file);
            int totalChunks = chunks.size();

            // ===== 阶段2：数据库向量化 =====
            progressTracker.switchPhase(taskId, "database", totalChunks,
                    "共 " + totalChunks + " 个切片，正在写入数据库...");

            for (int i = 0; i < totalChunks; i++) {
                ChunkResult c = chunks.get(i);
                vectorSearchService.addChunk(
                        textbookId, textbookTitle,
                        i + 1, c.startPage(), c.endPage(),
                        c.paragraphIndex(), c.content()
                );
                // 每 5 个切片更新一次进度
                if ((i + 1) % 5 == 0 || i == totalChunks - 1) {
                    progressTracker.update(taskId, i + 1,
                            "写入数据库: " + (i + 1) + "/" + totalChunks + " 切片");
                }
            }

            textbookService.updateStatus(textbookId, "completed");
            progressTracker.complete(taskId);
            log.info("教材异步处理完成: id={}, 切片数={}", textbookId, totalChunks);
        } catch (Throwable e) {
            log.error("教材异步处理失败: id={}", textbookId, e);
            textbookService.updateStatus(textbookId, "failed");
            progressTracker.fail(taskId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 异步执行教材删除（两阶段：云端存储 → 数据库）
     */
    @Async
    public CompletableFuture<Void> processDeleteAsync(String id, String title, int totalPages, String taskId) {
        try {
            log.info("开始异步删除教材: id={}, title={}", id, title);

            // ===== 阶段1：云端存储 =====
            progressTracker.update(taskId, 0, "正在删除云端存储文件...");
            try {
                fileStorageService.deleteTextbook(id);
            } catch (Throwable e) {
                log.warn("删除云端存储教材文件失败: id={}, error={}", id, e.getMessage(), e);
            }
            progressTracker.update(taskId, totalPages > 0 ? totalPages : 1, "云端存储文件已删除");

            // ===== 阶段2：数据库（无论是否有页数都清理切片，避免残留） =====
            progressTracker.switchPhase(taskId, "database", totalPages > 0 ? totalPages : 1,
                    "正在删除数据库切片...");
            try {
                vectorSearchService.deleteByTextbookId(id);
            } catch (Throwable e) {
                log.warn("删除数据库切片失败: id={}, error={}", id, e.getMessage(), e);
            }
            progressTracker.update(taskId, totalPages > 0 ? totalPages : 1, "数据库切片已删除");

            // 3. 删除教材元数据（教材与教学班的绑定随文档一并删除）
            textbookService.deleteById(id);

            progressTracker.complete(taskId);
            log.info("教材异步删除完成: id={}", id);
        } catch (Throwable e) {
            log.error("教材异步删除失败: id={}", id, e);
            progressTracker.fail(taskId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }
}