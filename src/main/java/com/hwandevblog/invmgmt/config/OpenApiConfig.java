package com.hwandevblog.invmgmt.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI inventoryManagementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("InvMgmt API")
                        .version("v1")
                        .description("재고 차감과 반품 정합성을 확인하는 API입니다."));
    }
}
