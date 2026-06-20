package com.companyx.equity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI equityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Equity P&L Service API")
                        .description("""
                                REST API for equity profit and loss, transactions, market data, and corporate actions.
                                Authenticate via POST /api/v1/auth/login, then use the returned JWT as a Bearer token.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Equity P&L Service")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT obtained from /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
