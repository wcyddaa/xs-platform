package com.xingshu.exam.service;

import com.xingshu.media.storage.StorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 作业附件文件存储服务
 * 
 * 本地磁盘 + AGC 云存储双写：
 * - 上传时：先存本地，再异步同步到 AGC
 * - 下载时：优先本地缓存，缺失时从 AGC 拉取
 */
@Service
public class HomeworkFileService {

    private static final Logger log = LoggerFactory.getLogger(HomeworkFileService.class);

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private Path homeworkDir;

    private final StorageService storageService;

    public HomeworkFileService(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostConstruct
    public void init() {
        homeworkDir = Paths.get(uploadDir, "homework");
        try {
            Files.createDirectories(homeworkDir);
            log.info("作业附件存储目录初始化完成: {}", homeworkDir);
        } catch (IOException e) {
            log.error("创建作业附件存储目录失败", e);
        }
    }

    /**
     * 保存作业附件（本地 + AGC 云存储）
     * 
     * @param homeworkId 作业模板 ID
     * @param fileName   原始文件名
     * @param bytes      文件字节数组
     * @return 保存后的本地文件路径
     */
    public Path saveAttachment(Long homeworkId, String fileName, byte[] bytes) throws IOException {
        // 按作业 ID 分目录，避免单目录文件过多
        Path hwDir = homeworkDir.resolve(String.valueOf(homeworkId));
        Files.createDirectories(hwDir);
        Path filePath = hwDir.resolve(fileName);
        Files.write(filePath, bytes);
        log.info("作业附件已保存到本地: {}", filePath);

        // 上传到云存储
        String cloudPath = "homework/" + homeworkId + "/" + fileName;
        try {
            storageService.uploadFile(bytes, "application/octet-stream", cloudPath, null);
        } catch (Exception e) {
            log.warn("作业附件上传云存储失败: homeworkId={}, fileName={}, error={}", homeworkId, fileName, e.getMessage());
        }
        return filePath;
    }

    /**
     * 获取作业附件文件（本地优先，缺失时从 AGC 下载）
     * 
     * @return 文件资源，不存在返回 null
     */
    public Resource getAttachment(Long homeworkId, String fileName) {
        Path hwDir = homeworkDir.resolve(String.valueOf(homeworkId));
        Path filePath = hwDir.resolve(fileName);

        if (Files.exists(filePath)) {
            return new FileSystemResource(filePath);
        }

        // 本地缺失，尝试从云存储下载
        String cloudPath = "homework/" + homeworkId + "/" + fileName;
        byte[] bytes = storageService.downloadFile(cloudPath);
        if (bytes != null) {
            try {
                Files.write(filePath, bytes);
                return new FileSystemResource(filePath);
            } catch (IOException e) {
                log.warn("写入下载文件失败: {}", filePath, e);
            }
        }

        return null;
    }

    /**
     * 删除作业的所有附件
     */
    public void deleteAttachments(Long homeworkId) {
        Path hwDir = homeworkDir.resolve(String.valueOf(homeworkId));
        if (Files.exists(hwDir)) {
            try {
                Files.walk(hwDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                // 删除云存储文件
                                if (Files.isRegularFile(p)) {
                                    String cloudPath = "homework/" + homeworkId + "/" + p.getFileName().toString();
                                    storageService.deleteFile(cloudPath);
                                }
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                log.warn("删除作业附件失败: {}", p, e);
                            }
                        });
                log.info("作业附件已全部删除: homeworkId={}", homeworkId);
            } catch (IOException e) {
                log.error("遍历作业附件目录失败: homeworkId={}", homeworkId, e);
            }
        }
    }

    /**
     * 将附件从 oldId 目录迁移到 newId 目录（模板创建后调用）
     */
    public void migrateAttachments(Long oldId, Long newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return;

        Path oldDir = homeworkDir.resolve(String.valueOf(oldId));
        Path newDir = homeworkDir.resolve(String.valueOf(newId));

        if (!Files.exists(oldDir)) return;

        try {
            Files.createDirectories(newDir);
            try (var stream = Files.list(oldDir)) {
                stream.forEach(file -> {
                    try {
                        Path target = newDir.resolve(file.getFileName().toString());
                        Files.move(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        log.info("作业附件已迁移: {} -> {}", file, target);
                    } catch (IOException e) {
                        log.warn("迁移附件失败: {}", file, e);
                    }
                });
            }
            // 删除空目录
            try {
                Files.deleteIfExists(oldDir);
            } catch (IOException ignored) {}
        } catch (IOException e) {
            log.error("迁移附件目录失败: oldId={}, newId={}", oldId, newId, e);
        }
    }
}