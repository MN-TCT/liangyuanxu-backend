package com.wedding.message.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 咨询聊天 WebSocket 处理器。
 * 连接地址：/ws/chat
 * token 通过 Sec-WebSocket-Protocol 子协议或 URL query 参数传递，
 * 由 {@link ChatHandshakeInterceptor} 在握手阶段校验，校验通过后把 userId 写入 session attributes。
 *
 * 客户端消息：
 *   {"type":"ping"}                      心跳
 *   {"type":"read","conversationId":1}   标记会话已读（可选）
 * 服务端推送：
 *   {"type":"message","conversationId":1,"sender":"admin","content":"...","createdAt":"...","senderName":"..."}
 *   {"type":"pong"}
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatSessionRegistry registry;

    public ChatWebSocketHandler(ChatSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // token 已在 ChatHandshakeInterceptor 中校验通过，这里直接取 userId
        Object attr = session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        if (!(attr instanceof Integer userId)) {
            // 理论上不会发生（拦截器已拦截），兜底关闭
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            return;
        }
        registry.register(userId, session);
        log.info("WS connected: userId={}, sessionId={}", userId, session.getId());
        // 回执
        send(session, "{\"type\":\"connected\"}");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 简单协议：心跳
        if (payload != null && payload.contains("\"ping\"")) {
            send(session, "{\"type\":\"pong\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
        log.info("WS closed: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.unregister(session);
        log.warn("WS transport error: sessionId={}, err={}", session.getId(), exception.getMessage());
    }

    private void send(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(text));
                }
            }
        } catch (Exception e) {
            log.warn("WS send failed: {}", e.getMessage());
        }
    }
}
