package com.xingshu.exam.service;

import com.xingshu.media.storage.FileCategory;
import com.xingshu.media.storage.MediaFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 作业附件文件服务。
 * <p>
 * 文件读写统一委托给 xs-media 的 {@link MediaFileStore}，
 * 按作业 ID 分目录存放在 homework 分类下，不再维护独立的 uploads 路径。
 */
@Service
public class HomeworkFileService {

    private static final Logger log = LoggerFactory.getLogger(HomeworkFileService.class);

    private static final String CONTENT_TYPE_BINARY = "application/octet-stream";

    private final MediaFileStore fileStore;

    public HomeworkFileService(MediaFileStore fileStore) {
        this.fileStore = fileStore;
    }

    /**
     * 保存作业附件（本地 + 云存储）
     *
     * @param homeworkId 作业模板 ID
     * @param fileName   原始文件名
     * @param bytes      文件字节数组
     * @return 保存后的本地文件路径
     */
    public Path saveAttachment(Long homeworkId, String fileName, byte[] bytes) throws IOException {
        return fileStore.save(FileCategory.HOMEWORK, String.valueOf(homeworkId), fileName, bytes, CONTENT_TYPE_BINARY);
    }

    /**
     * 获取作业附件文件（本地优先，缺失时从云存储回源）
     *
     * @return 文件资源，不存在返回 null
     */
    public Resource getAttachment(Long homeworkId, String fileName) {
        return fileStore.load(FileCategory.HOMEWORK, String.valueOf(homeworkId), fileName);
    }

    /** 删除作业的所有附件（本地 + 云存储） */
    public void deleteAttachments(Long homeworkId) {
        fileStore.deleteAll(FileCategory.HOMEWORK, String.valueOf(homeworkId));
        log.info("作业附件已全部删除: homeworkId={}", homeworkId);
    }

    /** 将附件从 oldId 目录迁移到 newId 目录（模板创建后调用） */
    public void migrateAttachments(Long oldId, Long newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) {
            return;
        }
        fileStore.move(FileCategory.HOMEWORK, String.valueOf(oldId), String.valueOf(newId));
    }
}
