package com.xingshu.common.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类（公共字段）
 */
@Data
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建者ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createrId;

    /** 更新者ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updaterId;
}