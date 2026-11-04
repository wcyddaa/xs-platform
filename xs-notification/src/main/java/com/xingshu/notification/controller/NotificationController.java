package com.xingshu.notification.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.common.base.PageResult;
import com.xingshu.notification.domain.po.Notification;
import com.xingshu.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 获取通知列表 */
    @GetMapping("/list")
    public ApiResponse<PageResult<Notification>> list(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(notificationService.getByUser(userId, pageNum, pageSize));
    }

    /** 获取未读数量 */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@RequestParam Long userId) {
        return ApiResponse.success(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public ApiResponse<Boolean> markRead(@PathVariable Long id) {
        return ApiResponse.success(notificationService.markRead(id));
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public ApiResponse<Boolean> markAllRead(@RequestParam Long userId) {
        return ApiResponse.success(notificationService.markAllRead(userId));
    }

    /** 发送通知（内部调用） */
    @PostMapping("/send")
    public ApiResponse<Notification> send(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String type = body.get("type").toString();
        String title = body.get("title").toString();
        String content = body.getOrDefault("content", "").toString();
        String redirectUrl = body.getOrDefault("redirectUrl", "").toString();
        return ApiResponse.success(notificationService.send(userId, type, title, content, redirectUrl));
    }
}