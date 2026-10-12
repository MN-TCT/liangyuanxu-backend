package com.wedding.message.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.message.module.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天消息推送服务：把新消息实时推给指定用户的在线连接。
 */
@Service
public class ChatPushService {

    private static final Logger log = LoggerFactory.getLogger(ChatPushService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatSessionRegistry registry;

    public ChatPushService(ChatSessionRegistry registry) {
        this.registry = registry;
    }

    /**
     * 推送一条聊天消息给指定用户（其所有在线端）
     * @param targetUserId 接收方用户ID
     * @param conversationId 会话ID
     * @param msg 消息实体
     */
    public void pushMessage(Integer targetUserId, Integer conversationId, ChatMessage msg) {
        if (targetUserId == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "message");
        payload.put("conversationId", conversationId);
        payload.put("id", msg.getId());
        payload.put("sender", msg.getSender());
        payload.put("senderName", msg.getSenderName());
        payload.put("content", msg.getContent());
        payload.put("msgType", msg.getMsgType());
        payload.put("createdAt", msg.getCreatedAt() == null ? null : msg.getCreatedAt().toString());
        pushRaw(targetUserId, payload);
    }

    /** 推送会话更新（例如未读数变化、新会话） */
    public void pushConversationUpdate(Integer targetUserId, Integer conversationId) {
        if (targetUserId == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "conversation_update");
        payload.put("conversationId", conversationId);
        pushRaw(targetUserId, payload);
    }

    private void pushRaw(Integer targetUserId, Map<String, Object> payload) {
        String text;
        try {
            text = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("serialize push payload failed: {}", e.getMessage());
            return;
        }
        for (WebSocketSession session : registry.getSessions(targetUserId)) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(text));
                    }
                }
            } catch (Exception e) {
                log.warn("push to userId={} failed: {}", targetUserId, e.getMessage());
            }
        }
    }
}
