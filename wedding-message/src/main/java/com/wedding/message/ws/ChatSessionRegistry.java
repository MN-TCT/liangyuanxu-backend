package com.wedding.message.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 在线会话注册表：userId -> sessions
 * 同一用户可能多端登录（小程序 + 管理端），故用 Set。
 */
@Component
public class ChatSessionRegistry {

    // userId -> 该用户的多个连接
    private final Map<Integer, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    // sessionId -> userId（便于断开时反查）
    private final Map<String, Integer> sessionUser = new ConcurrentHashMap<>();

    public void register(Integer userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionUser.put(session.getId(), userId);
    }

    public void unregister(WebSocketSession session) {
        if (session == null) return;
        Integer userId = sessionUser.remove(session.getId());
        if (userId == null) return;
        Set<WebSocketSession> set = userSessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) userSessions.remove(userId);
        }
    }

    /** 获取某用户的所有活跃连接 */
    public Set<WebSocketSession> getSessions(Integer userId) {
        if (userId == null) return java.util.Collections.emptySet();
        Set<WebSocketSession> set = userSessions.get(userId);
        return set == null ? java.util.Collections.emptySet() : set;
    }

    public boolean isOnline(Integer userId) {
        return !getSessions(userId).isEmpty();
    }
}
