package com.wedding.common;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前登录用户上下文工具。
 * <p>
 * 统一各微服务中重复的「从 Authorization 头解析 JWT」逻辑，避免每个 Controller
 * 各写一份 currentUserId() / currentRole()。
 * <p>
 * 解析失败或未登录时返回 null（由调用方决定是否抛异常），
 * 需要强制登录的场景请使用 {@link #requireUserId()} / {@link #requireAdmin()}。
 */
public final class AuthContext {

    private AuthContext() {
    }

    /** 从当前请求上下文取 Authorization 头 */
    private static String currentAuthHeader() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest().getHeader("Authorization");
    }

    /** 从请求头解析 Claims；无 token 或解析失败返回 null */
    public static Claims claims(HttpServletRequest request) {
        String header = request == null ? null : request.getHeader("Authorization");
        return parseHeader(header);
    }

    /** 从当前请求上下文解析 Claims；无 token 或解析失败返回 null */
    public static Claims currentClaims() {
        return parseHeader(currentAuthHeader());
    }

    private static Claims parseHeader(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            return JwtUtil.parseToken(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前用户 ID；未登录返回 null */
    public static Integer userId(HttpServletRequest request) {
        Claims c = claims(request);
        return c == null ? null : c.get("id", Integer.class);
    }

    /** 当前用户 ID（从请求上下文取）；未登录返回 null */
    public static Integer currentUserId() {
        Claims c = currentClaims();
        return c == null ? null : c.get("id", Integer.class);
    }

    /** 当前用户角色；未登录返回 null */
    public static String role(HttpServletRequest request) {
        Claims c = claims(request);
        return c == null ? null : c.get("role", String.class);
    }

    /** 当前用户角色（从请求上下文取）；未登录返回 null */
    public static String currentRole() {
        Claims c = currentClaims();
        return c == null ? null : c.get("role", String.class);
    }

    /** 是否管理员 */
    public static boolean isAdmin(HttpServletRequest request) {
        return "admin".equals(role(request));
    }

    /** 强制要求已登录，返回用户 ID；未登录抛 401 */
    public static Integer requireUserId(HttpServletRequest request) {
        Integer id = userId(request);
        if (id == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return id;
    }

    /** 强制要求管理员；非管理员抛 403 */
    public static void requireAdmin(HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new BizException(403, "无权限：仅管理员可操作");
        }
    }

    /**
     * 校验资源归属：非管理员只能操作自己（companyId 等于当前用户 ID）的资源。
     *
     * @param ownerCompanyId 资源所属商家 ID
     * @param request        当前请求
     */
    public static void checkOwner(Integer ownerCompanyId, HttpServletRequest request) {
        Integer userId = userId(request);
        String role = role(request);
        if (userId != null && !"admin".equals(role) && !userId.equals(ownerCompanyId)) {
            throw new BizException(403, "无权操作其他商家的数据");
        }
    }
}
