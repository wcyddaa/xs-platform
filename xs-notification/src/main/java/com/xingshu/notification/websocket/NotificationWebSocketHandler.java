package com.xingshu.notification.websocket;

import cn.hutool.json.JSONUtil;
import com.xingshu.notification.domain.po.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 消息推送处理器
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    /** userId -> session */
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("WebSocket 连接建立: userId={}", userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            userSessions.remove(userId);
            log.info("WebSocket 连接关闭: userId={}", userId);
        }
    }

    /** 向指定用户推送通知 */
    public void pushToUser(Long userId, Notification notification) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = JSONUtil.toJsonStr(notification);
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.error("WebSocket 推送失败: userId={}, error={}", userId, e.getMessage());
            }
        }
    }

    private Long getUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query != null && query.contains("userId=")) {
            try {
                return Long.parseLong(query.split("userId=")[1].split("&")[0]);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}