package com.xingshu.book.service;

import com.xingshu.media.storage.FileCategory;
import com.xingshu.media.storage.MediaFileStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 教材与课件文件服务。
 * <p>
 * 文件读写统一委托给 xs-media 的 {@link MediaFileStore}，
 * 教材与课件分别落到 textbooks / courseware 分类目录，不再维护各自的 uploads 路径。
 */
@Service
public class FileStorageService {

    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final MediaFileStore fileStore;

    public FileStorageService(MediaFileStore fileStore) {
        this.fileStore = fileStore;
    }

    /** 保存教材文件（本地 + 云存储） */
    public void saveTextbook(String id, String extension, byte[] bytes) throws IOException {
        fileStore.save(FileCategory.TEXTBOOK, null, id + "." + extension, bytes, CONTENT_TYPE_PDF);
    }

    /** 保存课件文件（本地 + 云存储） */
    public void saveCourseware(String id, String extension, byte[] bytes) throws IOException {
        fileStore.save(FileCategory.COURSEWARE, null, id + "." + extension, bytes, CONTENT_TYPE_PDF);
    }

    /** 获取教材文件（本地优先，缺失时从云存储回源） */
    public Resource getTextbookFile(String id) {
        return fileStore.loadById(FileCategory.TEXTBOOK, id);
    }

    /** 获取课件文件（本地优先，缺失时从云存储回源） */
    public Resource getCoursewareFile(String id) {
        return fileStore.loadById(FileCategory.COURSEWARE, id);
    }

    /** 读取教材文件原始字节，不存在返回 null */
    public byte[] readTextbookBytes(String id) throws IOException {
        return fileStore.readById(FileCategory.TEXTBOOK, id);
    }

    /** 删除教材文件（本地 + 云存储） */
    public void deleteTextbook(String id) {
        fileStore.deleteById(FileCategory.TEXTBOOK, id);
    }

    /** 删除课件文件（本地 + 云存储） */
    public void deleteCourseware(String id) {
        fileStore.deleteById(FileCategory.COURSEWARE, id);
    }
}
