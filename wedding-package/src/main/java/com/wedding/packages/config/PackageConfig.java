package com.wedding.packages.config;

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
public class PackageConfig implements WebMvcConfigurer {

    @Value("${wedding.cover-dir}")
    private String coverDir;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    // 静态映射已上传的套餐封面图：/api/package/static/** -> 磁盘目录
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String abs = Paths.get(coverDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/api/package/static/**")
                .addResourceLocations("file:" + abs + "/");
    }

    @Component
    public static class AuthFilter extends JwtAuthFilter {
        @Override
        protected boolean isPublic(String path) {
            return super.isPublic(path)
                    || path.startsWith("/api/package/health")
                    || path.startsWith("/api/package/list")
                    || path.startsWith("/api/package/home/pinned")
                    || path.startsWith("/api/package/categories")
                    || path.startsWith("/api/package/companies")
                    || path.startsWith("/api/package/static")
                    || path.matches("^/api/package/\\d+$");
        }
    }
}
