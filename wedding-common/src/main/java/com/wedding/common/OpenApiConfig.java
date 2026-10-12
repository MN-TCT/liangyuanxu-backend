package com.wedding.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置（各业务服务通过依赖 wedding-common 自动生效）。
 * <p>
 * 访问：http://localhost:{port}/swagger-ui/index.html
 * 文档：http://localhost:{port}/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI weddingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("婚庆婚纱摄影 - 微服务接口文档")
                        .description("婚庆平台后端 API（含 JWT 鉴权）。点击右上角 Authorize 填入登录返回的 token 即可调试受保护接口。")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token，无需手动加 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
