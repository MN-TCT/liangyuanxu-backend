package com.wedding.message.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;
import java.util.Map;

/**
 * 注册咨询聊天 WebSocket 端点：/ws/chat
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;

    public WebSocketConfig(ChatWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new ChatHandshakeInterceptor())
                .setHandshakeHandler(echoSubProtocolHandshakeHandler());
    }

    /**
     * 自定义握手处理器：回显客户端请求的子协议。
     * 客户端通过 Sec-WebSocket-Protocol: bearer, <token> 传递 token，
     * 浏览器要求服务端必须回显其中一个子协议，否则拒绝连接。
     * 这里固定回显 "bearer"（token 本身通过握手头读取，无需回显）。
     */
    @Bean
    public DefaultHandshakeHandler echoSubProtocolHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler handler) {
                if (requestedProtocols != null && requestedProtocols.contains("bearer")) {
                    return "bearer";
                }
                return super.selectProtocol(requestedProtocols, handler);
            }

            @Override
            protected boolean isValidOrigin(ServerHttpRequest request) {
                return true;
            }
        };
    }
}

