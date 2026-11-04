package com.lifelog.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LifeLog API")
                        .description("LifeLog 커리어 기반 연봉 예측 서비스 API 명세입니다.")
                        .version("v1"))
                .servers(List.of(
                        new Server()
                                .url("/")
                                .description("Current server")
                ));
    }
}
