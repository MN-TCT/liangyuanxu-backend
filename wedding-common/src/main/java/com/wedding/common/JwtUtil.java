package com.wedding.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具（各微服务共享同一密钥）
 */
public class JwtUtil {

    private static final String SECRET = resolveSecret();
    private static final long EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L; // 7天

    /**
     * 读取 JWT 签名密钥。
     * 生产环境必须通过环境变量 JWT_SECRET 注入（至少 32 字节），
     * 未配置时回退到仅用于本地开发的默认值，并打印醒目告警。
     */
    private static String resolveSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            // 本地开发兜底：仅在未显式配置时使用，生产环境务必设置 JWT_SECRET
            System.err.println("[JwtUtil][WARN] 未检测到环境变量 JWT_SECRET，正在使用不安全的开发默认密钥。"
                    + "生产环境请务必配置 JWT_SECRET（至少 32 字节随机字符串）。");
            return "dev-only-insecure-secret-change-me-please-000000000000";
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节，存在被暴力破解风险，请更换更长的随机密钥");
        }
        return secret;
    }

    private static SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateToken(Integer id, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("username", username);
        claims.put("role", role);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_MS))
                .signWith(key())
                .compact();
    }

    /**
     * 校验并解析 token，返回 Claims；非法/过期抛 JwtException
     */
    public static Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
    }
}
