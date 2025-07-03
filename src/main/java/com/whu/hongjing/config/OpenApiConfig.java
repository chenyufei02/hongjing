package com.whu.hongjing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("泓镜客户管理系统 API")
                        .version("1.0.0")
                        .description("泓镜客户管理系统的REST API接口文档")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("chenqi02@whu.edu.cn")
                        )
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("本地开发环境")
                ));
    }
}