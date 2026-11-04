package com.xingshu.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingshu.notification.domain.po.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}