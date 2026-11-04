package com.xingshu.media.storage;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 统一云存储接口 - 对业务代码完全透明
 * <p>
 * 使用方式：直接注入 StorageService，无需关心底层是哪个云厂商
 * <pre>{@code
 * @Autowired
 * private StorageService storageService;
 * }</pre>
 * 切换云厂商只需修改 application.yml 中的 xingshu.storage.type
 */
public interface StorageService {

    /**
     * 上传文件
     *
     * @param file     文件
     * @param path     云端路径（如 "textbooks/123.pdf"）
     * @param metadata 自定义元数据（可为 null）
     * @return 文件访问 URL
     */
    String uploadFile(MultipartFile file, String path, Map<String, String> metadata);

    /**
     * 上传文件（字节数组）
     */
    String uploadFile(byte[] bytes, String contentType, String path, Map<String, String> metadata);

    /**
     * 下载文件
     *
     * @param path 云端路径
     * @return 文件字节数组，不存在返回 null
     */
    byte[] downloadFile(String path);

    /**
     * 生成临时访问 URL（带有效期）
     *
     * @param path           云端路径
     * @param expireDuration 有效期
     * @return 临时访问 URL
     */
    String generatePresignedUrl(String path, Duration expireDuration);

    /**
     * 删除文件
     *
     * @param path 云端路径
     * @return 是否删除成功
     */
    boolean deleteFile(String path);

    /**
     * 检查文件是否存在
     */
    boolean exists(String path);

    /**
     * 列出指定前缀下的所有文件
     */
    List<String> listFiles(String prefix);
}