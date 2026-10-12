package com.wedding.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * JWT 鉴权过滤器（普通 Servlet 应用使用，非网关）。
 * 由各业务微服务通过 @Component 启用，或按需排除公开接口。
 */
public abstract class JwtAuthFilter implements Filter {

    /**
     * 该服务中需要放行（无需登录）的路径前缀，子类可覆盖
     */
    protected boolean isPublic(String path) {
        return path.startsWith("/api/health")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                // OpenAPI / Swagger 文档
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse res = (HttpServletResponse) servletResponse;
        String path = req.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(req.getMethod()) || isPublic(path)) {
            chain.doFilter(req, res);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            write401(res, "未登录或 token 缺失");
            return;
        }
        String token = header.substring(7);
        try {
            JwtUtil.parseToken(token);
        } catch (Exception e) {
            write401(res, "token 无效或已过期");
            return;
        }
        chain.doFilter(req, res);
    }

    private void write401(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
