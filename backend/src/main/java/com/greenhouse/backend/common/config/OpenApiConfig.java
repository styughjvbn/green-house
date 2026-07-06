package com.greenhouse.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI greenhouseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Greenhouse Backend API")
                        .description("난 농장 관리 시스템 Backend API")
                        .version("0.0.1"));
    }
}