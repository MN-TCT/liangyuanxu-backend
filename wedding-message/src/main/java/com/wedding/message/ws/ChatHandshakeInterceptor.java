package com.wedding.message.ws;

import com.wedding.common.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手拦截器：在 HTTP 升级阶段校验 token。
 * <p>
 * 校验失败时直接返回 401，避免"先建立连接、再被服务端关闭"的窗口期。
 * token 来源优先级：
 * <ol>
 *   <li>{@code Sec-WebSocket-Protocol: bearer, <token>} 子协议（推荐，避免 token 进 URL/访问日志）</li>
 *   <li>URL query 参数 {@code ?token=***}（兼容旧客户端）</li>
 * </ol>
 * 校验通过后把 userId 写入 attributes，供 {@link ChatWebSocketHandler} 使用。
 */
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChatHandshakeInterceptor.class);

    /** 握手 attributes 中存放已认证 userId 的键 */
    public static final String ATTR_USER_ID = "userId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveTokenFromSubProtocol(request);
        if (token == null || token.isEmpty()) {
            token = resolveTokenFromQuery(request);
        }
        if (token == null || token.isEmpty()) {
            reject(response, "token missing");
            return false;
        }
        try {
            Claims c = JwtUtil.parseToken(token);
            Integer userId = c.get("id", Integer.class);
            if (userId == null) {
                reject(response, "invalid token payload");
                return false;
            }
            attributes.put(ATTR_USER_ID, userId);
            return true;
        } catch (Exception e) {
            reject(response, "invalid token");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private void reject(ServerHttpResponse response, String reason) {
        log.warn("WS handshake rejected: {}", reason);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    /** 从 Sec-WebSocket-Protocol 头解析 token（格式：bearer, <token>） */
    private String resolveTokenFromSubProtocol(ServerHttpRequest request) {
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocols == null || protocols.isEmpty()) {
            return null;
        }
        for (String part : protocols.split(",")) {
            String p = part.trim();
            if (!p.isEmpty() && !"bearer".equalsIgnoreCase(p)) {
                return p;
            }
        }
        return null;
    }

    /** 从 URL query 参数 token 解析（兼容旧客户端） */
    private String resolveTokenFromQuery(ServerHttpRequest request) {
        URI uri = request.getURI();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && "token".equals(kv.substring(0, i))) {
                return kv.substring(i + 1);
            }
        }
        return null;
    }
}
