package com.xingshu.notification.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private Boolean isRead;
    private String redirectUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createrId;
    private Long updaterId;
}