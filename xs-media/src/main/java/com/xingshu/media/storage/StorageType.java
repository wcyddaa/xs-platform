package com.xingshu.media.storage;

/**
 * 云存储类型枚举
 */
public enum StorageType {
    /** 华为 AGC */
    AGC,
    /** 阿里云 OSS */
    OSS,
    /** 腾讯云 COS */
    COS,
    /** AWS S3（兼容 MinIO 等） */
    S3
}