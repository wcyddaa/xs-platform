package com.xingshu.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台智能体总 quota（单行，id=1）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_quota")
public class PlatformQuota extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台总额度（token） */
    private Long totalTokenLimit;

    /** 全平台累计消耗 token */
    private Long tokenUsed;
}
