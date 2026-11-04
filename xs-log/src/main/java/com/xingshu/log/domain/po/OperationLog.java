package com.xingshu.log.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者ID */
    private Long operatorId;

    /** 操作者姓名 */
    private String operatorName;

    /** 操作IP */
    private String operatorIp;

    /** 用户代理 */
    private String userAgent;

    /** 操作类型 */
    private String operationType;

    /** 目标类型 */
    private String targetType;

    /** 目标ID */
    private String targetId;

    /** 目标名称 */
    private String targetName;

    /** 状态：SUCCESS/FAIL */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时(毫秒) */
    private Long durationMs;

    /** 创建时间 */
    private LocalDateTime createTime;
}