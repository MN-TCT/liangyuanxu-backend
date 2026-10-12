package com.wedding.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * WebSocket 握手鉴权过滤器。
 * <p>
 * 背景：Spring Cloud Gateway 的 WebsocketRoutingFilter 会先向客户端返回 101，
 * 再与后端建立连接，因此后端返回的 401 无法透传给客户端（客户端会先收到 open）。
 * 这里在路由转发前于网关侧校验 token，校验失败直接返回 401，避免"假连接"。
 * <p>
 * 仅作用于 {@code /ws/**} 路径；token 来源：
 * <ol>
 *   <li>{@code Sec-WebSocket-Protocol: bearer, <token>} 子协议</li>
 *   <li>URL query 参数 {@code ?token=...}（兼容旧客户端）</li>
 * </ol>
 * 说明：网关为 WebFlux 响应式栈，不能依赖基于 Servlet 的 wedding-common，
 * 因此这里直接使用 jjwt 校验，密钥与各业务服务保持一致（环境变量 JWT_SECRET）。
 */
@Component
public class WebSocketAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthFilter.class);

    private final SecretKey key;

    public WebSocketAuthFilter(@Value("${wedding.jwt.secret:}") String secret) {
        String s = (secret == null || secret.isBlank()) ? System.getenv("JWT_SECRET") : secret;
        if (s == null || s.isBlank()) {
            log.warn("[WebSocketAuthFilter] 未配置 JWT_SECRET，使用不安全的开发默认密钥");
            s = "dev-only-insecure-secret-change-me-please-000000000000";
        }
        if (s.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节，请更换更长的随机密钥");
        }
        this.key = Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path == null || !path.startsWith("/ws/")) {
            return chain.filter(exchange);
        }

        String token = resolveToken(request);
        if (token == null || token.isEmpty()) {
            return reject(exchange, "token missing");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (claims.get("id", Integer.class) == null) {
                return reject(exchange, "invalid token payload");
            }
        } catch (Exception e) {
            return reject(exchange, "invalid token");
        }
        return chain.filter(exchange);
    }

    private String resolveToken(ServerHttpRequest request) {
        // 1. 子协议
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocols != null && !protocols.isEmpty()) {
            for (String part : protocols.split(",")) {
                String p = part.trim();
                if (!p.isEmpty() && !"bearer".equalsIgnoreCase(p)) {
                    return p;
                }
            }
        }
        // 2. query 参数
        return request.getQueryParams().getFirst("token");
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        log.warn("WS handshake rejected at gateway: {} path={}", reason, exchange.getRequest().getURI().getPath());
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"未授权：WebSocket token 无效或缺失\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 早于 WebsocketRoutingFilter，确保在升级握手前完成校验
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
