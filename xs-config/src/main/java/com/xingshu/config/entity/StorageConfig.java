package com.xingshu.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 对象存储配置（平台级，仅管理员维护）。
 * <p>
 * clientSecret 在库中以 AES-GCM 密文保存，接口对外永不返回明文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("storage_config")
public class StorageConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 存储类型：agc-华为AGC / oss-阿里云 / cos-腾讯云 / local-仅本地 */
    private String provider;

    /** 接入地址：OSS endpoint；AGC/COS 可留空；local 不使用 */
    private String endpoint;

    private String bucket;

    private String region;

    /** AGC client_id（非敏感） */
    private String clientId;

    /** AGC client_secret 密文（enc:v1:...） */
    private String clientSecret;

    /** 测试状态：untested / success / fail */
    private String status;

    /** 最近一次测试结果说明 */
    private String testMessage;

    private LocalDateTime lastTestTime;

    /** 是否启用：1-启用 0-停用 */
    private Integer enabled;
}
