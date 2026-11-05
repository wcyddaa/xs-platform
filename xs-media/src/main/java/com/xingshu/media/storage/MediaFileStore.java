package com.xingshu.media.storage;

import com.xingshu.media.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * 平台统一文件存储。
 * <p>
 * 由 xs-media 统一管理教材、课件、作业附件的落盘与云同步：
 * <ul>
 *   <li>本地根目录固定为 {@code xingshu.storage.local-root}，与启动模块的工作目录无关；</li>
 *   <li>分类目录固定为 textbooks / courseware / homework，云端对象前缀同名；</li>
 *   <li>写入时先落本地再同步云存储，读取时本地优先、缺失再从云端回源。</li>
 * </ul>
 */
public class MediaFileStore {

    private static final Logger log = LoggerFactory.getLogger(MediaFileStore.class);

    private final Path root;
    private final StorageService storageService;

    public MediaFileStore(StorageProperties properties, StorageService storageService) {
        this.root = Paths.get(properties.getLocalRoot()).toAbsolutePath().normalize();
        this.storageService = storageService;
        try {
            Files.createDirectories(root);
            for (FileCategory category : FileCategory.values()) {
                Files.createDirectories(root.resolve(category.getDir()));
            }
            log.info("统一文件存储根目录初始化完成: {}", root);
        } catch (IOException e) {
            log.error("创建统一文件存储目录失败: {}", root, e);
        }
    }

    public Path root() {
        return root;
    }

    /** 本地目录：{root}/{category}[/{key}] */
    public Path dir(FileCategory category, String key) {
        Path path = root.resolve(category.getDir());
        return (key == null || key.isBlank()) ? path : path.resolve(safeName(key));
    }

    public Path path(FileCategory category, String key, String fileName) {
        return dir(category, key).resolve(safeName(fileName));
    }

    /** 保存文件：只写到当前启用的存储（云端启用时不再同时落本地） */
    public Path save(FileCategory category, String key, String fileName, byte[] bytes, String contentType) throws IOException {
        Path target = path(category, key, fileName);
        if (storageService.isRemoteEnabled()) {
            String cloudPath = cloudPath(category, key, fileName);
            try {
                storageService.uploadFile(bytes, contentType, cloudPath, null);
                log.info("{} 已保存到云端存储: {}", category, cloudPath);
                return target;
            } catch (Throwable e) {
                log.error("{} 写入云端存储失败: path={}, error={}", category, cloudPath, e.getMessage());
                throw new IOException("云存储写入失败: " + e.getMessage(), e);
            }
        }
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        log.info("{} 已保存到本地（仅本地存储）: {}", category, target);
        return target;
    }

    /** 按 ID 前缀查找本地文件（教材/课件一个 ID 只保留一个文件） */
    public Path find(FileCategory category, String id) {
        Path dir = dir(category, null);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(id + "."))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("遍历 {} 目录失败: {}", category, e.getMessage());
            return null;
        }
    }

    /** 精确路径读取：启用云端时以云端为准（仅兼容读取历史本地文件），不再回写本地 */
    public Resource load(FileCategory category, String key, String fileName) {
        Path local = path(category, key, fileName);
        if (!storageService.isRemoteEnabled()) {
            return Files.exists(local) ? new FileSystemResource(local) : null;
        }
        String cloudPath = cloudPath(category, key, fileName);
        byte[] bytes = storageService.downloadFile(cloudPath);
        if (bytes != null) {
            return new ByteArrayResource(bytes);
        }
        // 云端没有时兜底历史本地文件，避免升级前上传的资料无法预览
        if (Files.exists(local)) {
            log.info("云端缺失，使用历史本地文件: {}", local);
            return new FileSystemResource(local);
        }
        return null;
    }

    /** 按 ID 读取（未知扩展名时按前缀匹配，云端回源默认 .pdf） */
    public Resource loadById(FileCategory category, String id) {
        if (storageService.isRemoteEnabled()) {
            Resource remote = load(category, null, id + ".pdf");
            if (remote != null) {
                return remote;
            }
            Path legacy = find(category, id);
            return legacy != null ? new FileSystemResource(legacy) : null;
        }
        Path local = find(category, id);
        return local != null ? new FileSystemResource(local) : null;
    }

    /** 按 ID 读取原始字节，不存在返回 null */
    public byte[] readById(FileCategory category, String id) throws IOException {
        Resource resource = loadById(category, id);
        if (resource == null || !resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** 删除单个文件（本地 + 云端） */
    public void delete(FileCategory category, String key, String fileName) {
        Path local = path(category, key, fileName);
        try {
            Files.deleteIfExists(local);
            log.info("已删除本地 {}: {}", category, local);
        } catch (IOException e) {
            log.warn("删除本地 {} 失败: {}, error={}", category, local, e.getMessage());
        }
        deleteCloud(cloudPath(category, key, fileName));
    }

    /** 按 ID 删除（教材/课件，扩展名未知时按前缀匹配） */
    public void deleteById(FileCategory category, String id) {
        Path local = find(category, id);
        if (local != null) {
            delete(category, null, local.getFileName().toString());
            return;
        }
        deleteCloud(cloudPath(category, null, id + ".pdf"));
    }

    /** 删除某个 key 下的全部文件（作业附件按作业 ID 分目录） */
    public void deleteAll(FileCategory category, String key) {
        if (storageService.isRemoteEnabled()) {
            String prefix = cloudPath(category, key, "");
            try {
                for (String path : storageService.listFiles(prefix)) {
                    storageService.deleteFile(path);
                }
            } catch (Throwable e) {
                log.warn("删除云端 {} 目录失败: key={}, error={}", category, key, e.getMessage());
            }
        }
        Path dir = dir(category, key);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除本地 {} 失败: {}", category, p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("遍历 {} 目录失败: key={}", category, key, e);
        }
    }

    /** 将某个 key 下的本地文件迁移到新的 key（作业附件 ID 变更时使用） */
    public void move(FileCategory category, String fromKey, String toKey) {
        if (fromKey == null || toKey == null || fromKey.equals(toKey)) {
            return;
        }
        Path from = dir(category, fromKey);
        Path to = dir(category, toKey);
        if (!Files.isDirectory(from)) {
            return;
        }
        try {
            Files.createDirectories(to);
            try (var stream = Files.list(from)) {
                stream.forEach(file -> {
                    try {
                        Files.move(file, to.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                        log.info("{} 文件已迁移: {} -> {}", category, fromKey, toKey);
                    } catch (IOException e) {
                        log.warn("迁移 {} 文件失败: {}, error={}", category, file, e.getMessage());
                    }
                });
            }
            Files.deleteIfExists(from);
        } catch (IOException e) {
            log.error("迁移 {} 目录失败: {} -> {}", category, fromKey, toKey, e);
        }
    }

    private String cloudPath(FileCategory category, String key, String fileName) {
        return (key == null || key.isBlank())
                ? category.getDir() + "/" + fileName
                : category.getDir() + "/" + key + "/" + fileName;
    }

    private void deleteCloud(String cloudPath) {
        try {
            storageService.deleteFile(cloudPath);
        } catch (Throwable e) {
            log.warn("删除云端文件失败: path={}, error={}", cloudPath, e.getMessage());
        }
    }

    /** 文件名/目录 key 只允许单层名称，防止路径穿越 */
    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return name.replaceAll("[\\\\/]", "_").replace("..", "_");
    }
}
