package com.wedding.message.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.wedding.common.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Configuration
public class MessageConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Component
    public static class AuthFilter extends JwtAuthFilter {
        @Override
        protected boolean isPublic(String path) {
            return super.isPublic(path)
                    || path.startsWith("/ws/")
                    || path.contains("/customer-message")
                    || path.startsWith("/api/message/my-conversations")
                    || path.startsWith("/api/message/my-unread-count")
                    || path.contains("/user-messages")
                    || path.startsWith("/api/message/health");
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) servletRequest;
            String path = req.getRequestURI();
            String method = req.getMethod();
            // 仅放行用户端「发起咨询」：POST /api/message/conversations
            // 商家端「会话列表」：GET /api/message/conversations 仍需登录
            if ("POST".equalsIgnoreCase(method) && path.equals("/api/message/conversations")) {
                chain.doFilter(servletRequest, servletResponse);
                return;
            }
            super.doFilter(servletRequest, servletResponse, chain);
        }
    }
}
