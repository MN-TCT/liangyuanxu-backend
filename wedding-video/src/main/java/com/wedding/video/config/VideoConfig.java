package com.wedding.video.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.wedding.common.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class VideoConfig implements WebMvcConfigurer {

    @Value("${wedding.upload-dir}")
    private String uploadDir;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    // 静态映射已上传视频：/api/video/static/** -> 磁盘目录
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String abs = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/api/video/static/**")
                .addResourceLocations("file:" + abs + "/");
    }

    @Component
    public static class AuthFilter extends JwtAuthFilter {
        @Override
        protected boolean isPublic(String path) {
            return super.isPublic(path)
                    || path.startsWith("/api/video/health")
                    || path.startsWith("/api/video/list")
                    || path.startsWith("/api/video/static")
                    || path.matches("^/api/video/\\d+$");
        }
    }
}
