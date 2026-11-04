package com.xingshu.book.service;

import com.xingshu.media.storage.StorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private Path textbookDir;
    private Path coursewareDir;

    private final StorageService storageService;

    public FileStorageService(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostConstruct
    public void init() {
        Path base = Paths.get(uploadDir);
        textbookDir = base.resolve("textbooks");
        coursewareDir = base.resolve("courseware");
        try {
            Files.createDirectories(textbookDir);
            Files.createDirectories(coursewareDir);
            log.info("文件存储目录初始化完成: textbooks={}, courseware={}", textbookDir, coursewareDir);
        } catch (IOException e) {
            log.error("创建文件存储目录失败", e);
        }
    }

    /** 保存教材文件（本地 + 云存储） */
    public void saveTextbook(String id, String extension, byte[] bytes) throws IOException {
        Path filePath = textbookDir.resolve(id + "." + extension);
        Files.write(filePath, bytes);
        log.info("教材文件已保存到本地: {}", filePath);
        String cloudPath = "textbooks/" + id + "." + extension;
        try {
            storageService.uploadFile(bytes, "application/pdf", cloudPath, null);
        } catch (Throwable e) {
            log.warn("教材上传云存储失败: id={}, error={}", id, e.getMessage(), e);
        }
    }

    /** 保存课件文件（本地 + 云存储） */
    public void saveCourseware(String id, String extension, byte[] bytes) throws IOException {
        Path filePath = coursewareDir.resolve(id + "." + extension);
        Files.write(filePath, bytes);
        log.info("课件文件已保存到本地: {}", filePath);
        String cloudPath = "courseware/" + id + "." + extension;
        try {
            storageService.uploadFile(bytes, "application/pdf", cloudPath, null);
        } catch (Throwable e) {
            log.warn("课件上传云存储失败: id={}, error={}", id, e.getMessage(), e);
        }
    }

    /** 获取教材文件（本地优先，缺失时从云存储下载） */
    public Resource getTextbookFile(String id) {
        try {
            Resource local = resolveFile(textbookDir, id);
            if (local != null) return local;
            String cloudPath = "textbooks/" + id + ".pdf";
            byte[] bytes = storageService.downloadFile(cloudPath);
            if (bytes != null) {
                Path localPath = textbookDir.resolve(id + ".pdf");
                Files.write(localPath, bytes);
                return new FileSystemResource(localPath);
            }
        } catch (Throwable e) {
            log.warn("获取教材文件失败: id={}, error={}", id, e.getMessage(), e);
        }
        return null;
    }

    /** 获取课件文件（本地优先，缺失时从云存储下载） */
    public Resource getCoursewareFile(String id) {
        try {
            Resource local = resolveFile(coursewareDir, id);
            if (local != null) return local;
            String cloudPath = "courseware/" + id + ".pdf";
            byte[] bytes = storageService.downloadFile(cloudPath);
            if (bytes != null) {
                Path localPath = coursewareDir.resolve(id + ".pdf");
                Files.write(localPath, bytes);
                return new FileSystemResource(localPath);
            }
        } catch (Throwable e) {
            log.warn("获取课件文件失败: id={}, error={}", id, e.getMessage(), e);
        }
        return null;
    }

    /** 读取教材文件原始字节（本地优先，缺失时从云存储下载；不存在返回 null） */
    public byte[] readTextbookBytes(String id) throws IOException {
        Resource resource = getTextbookFile(id);
        if (resource == null || !resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private Resource resolveFile(Path dir, String id) throws IOException {
        try (var stream = Files.list(dir)) {
            Path found = stream
                    .filter(p -> p.getFileName().toString().startsWith(id + "."))
                    .findFirst()
                    .orElse(null);
            if (found != null && Files.exists(found)) {
                return new FileSystemResource(found);
            }
        }
        return null;
    }

    /** 删除教材文件（本地 + 云存储） */
    public void deleteTextbook(String id) {
        // 删除本地文件
        try (var stream = Files.list(textbookDir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(id + "."))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            log.info("已删除本地教材文件: {}", p);
                        } catch (IOException e) {
                            log.warn("删除本地教材文件失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("遍历教材目录失败: {}", e.getMessage());
        }
        // 删除云存储文件
        try {
            String cloudPath = "textbooks/" + id + ".pdf";
            storageService.deleteFile(cloudPath);
        } catch (Throwable e) {
            log.warn("删除云存储教材文件失败: id={}, error={}", id, e.getMessage(), e);
        }
    }
}