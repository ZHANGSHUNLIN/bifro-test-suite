package com.baidu.duhome.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置类
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 文档基本信息
     */
    @Bean
    public OpenAPI bifroTestBedOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bifro Test Bed API")
                        .description("MQTT 测试套件管理平台 API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Bifro Team"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("本地开发服务器")
                ));
    }
}
