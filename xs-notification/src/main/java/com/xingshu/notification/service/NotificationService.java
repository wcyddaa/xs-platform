package com.xingshu.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.common.base.PageResult;
import com.xingshu.notification.domain.po.Notification;
import com.xingshu.notification.mapper.NotificationMapper;
import com.xingshu.notification.websocket.NotificationWebSocketHandler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    private final NotificationWebSocketHandler webSocketHandler;

    public NotificationService(NotificationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /** 发送通知（持久化 + WebSocket 实时推送） */
    public Notification send(Long userId, String type, String title, String content, String redirectUrl) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRedirectUrl(redirectUrl);
        notification.setIsRead(false);
        save(notification);
        // WebSocket 实时推送
        webSocketHandler.pushToUser(userId, notification);
        return notification;
    }

    /** 批量发送通知 */
    public void sendBatch(List<Long> userIds, String type, String title, String content, String redirectUrl) {
        for (Long userId : userIds) {
            send(userId, type, title, content, redirectUrl);
        }
    }

    /** 获取用户通知列表 */
    public PageResult<Notification> getByUser(Long userId, int pageNum, int pageSize) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime);
        Page<Notification> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 获取用户未读通知数 */
    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false);
        return count(wrapper);
    }

    /** 标记已读 */
    public boolean markRead(Long id) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setIsRead(true);
        return updateById(notification);
    }

    /** 全部标记已读 */
    public boolean markAllRead(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false);
        Notification notification = new Notification();
        notification.setIsRead(true);
        return update(notification, wrapper);
    }
}