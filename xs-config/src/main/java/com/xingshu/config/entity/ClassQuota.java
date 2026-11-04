package com.xingshu.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教学班智能体额度，班内所有智能体共享
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("class_quota")
public class ClassQuota extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 教学班ID */
    private Long classId;

    /** 教学班分配额度（token） */
    private Long totalTokenLimit;

    /** 教学班累计消耗 token */
    private Long tokenUsed;
}
