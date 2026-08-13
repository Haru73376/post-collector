package com.github.haru73376.post_collector.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Post Collector API",
                version = "v1",
                description = OpenApiConfig.API_DESCRIPTION
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

    static final String API_DESCRIPTION = """
            A REST API for saving and organizing social media posts, across platforms, into hierarchical \
            categories and cross-cutting tags.

            ### How to try this out
            1. **Authentication** → `POST /auth/register`, then `POST /auth/login`.
            2. Copy the `accessToken` from the login response.
            3. Click the **Authorize** button (top right) and paste the token (no `Bearer ` prefix — it's added automatically).
            4. All other endpoints will now include it automatically. Tokens expire after 15 minutes; log in again to get a fresh one.
            """;

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth";

    // The order a first-time reader would actually walk through the API. Anything not
    // listed here keeps whatever position springdoc gave it (defensive default, shouldn't
    // happen since every controller has a @Tag matching one of these names).
    private static final List<String> TAG_DISPLAY_ORDER =
            List.of("Authentication", "Categories", "Tags", "Posts", "User Profile");

    // Defining the bearerAuth scheme above only makes the "Authorize" button appear;
    // it doesn't attach the token to any request. Swagger UI only auto-attaches the
    // authorized credential to operations the spec marks as requiring it, so every
    // non-auth endpoint needs that marker added here (SecurityConfig's PUBLIC_PATHS
    // is the equivalent boundary on the actual enforcement side). While we're marking
    // an endpoint as requiring auth, also document the 401 every one of them can return —
    // this is deliberately not left to individual @ApiResponse annotations since it's
    // true of every endpoint here and nowhere else (login/refresh have their own 401
    // with a different meaning and are excluded by the same AUTH_PATH_PREFIX check).
    @Bean
    public GlobalOpenApiCustomizer bearerAuthRequirementCustomizer() {
        ApiResponse unauthorized = new ApiResponse()
                .description("Missing, invalid, or expired access token")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));

        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.startsWith(AUTH_PATH_PREFIX)) {
                pathItem.readOperations().forEach(operation -> {
                    operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                    operation.getResponses().addApiResponse("401", unauthorized);
                });
            }
        });
    }

    // Without this, Swagger UI groups endpoints in whatever order springdoc happens to
    // discover the controllers in while scanning the classpath (looks arbitrary). Re-sorting
    // the already-built tag list (each entry's name/description still comes from the
    // per-controller @Tag) after the fact, rather than declaring a second competing tag list,
    // avoids springdoc creating duplicate tag entries for the same name.
    @Bean
    public GlobalOpenApiCustomizer tagOrderCustomizer() {
        return openApi -> {
            List<Tag> tags = openApi.getTags();
            if (tags != null) {
                tags.sort(Comparator.comparingInt(tag -> {
                    int index = TAG_DISPLAY_ORDER.indexOf(tag.getName());
                    return index == -1 ? Integer.MAX_VALUE : index;
                }));
            }
        };
    }
}
