package com.github.haru73376.post_collector.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Post Collector API",
                version = "v1",
                description = "A web application API for centrally managing SNS post URLs"
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth";

    // Defining the bearerAuth scheme above only makes the "Authorize" button appear;
    // it doesn't attach the token to any request. Swagger UI only auto-attaches the
    // authorized credential to operations the spec marks as requiring it, so every
    // non-auth endpoint needs that marker added here (SecurityConfig's PUBLIC_PATHS
    // is the equivalent boundary on the actual enforcement side).
    @Bean
    public GlobalOpenApiCustomizer bearerAuthRequirementCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.startsWith(AUTH_PATH_PREFIX)) {
                pathItem.readOperations().forEach(
                        operation -> operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                );
            }
        });
    }
}
