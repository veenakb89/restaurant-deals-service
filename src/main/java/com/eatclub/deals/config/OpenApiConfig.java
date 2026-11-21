package com.eatclub.deals.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Name of the bearer-token scheme; endpoints that need a token refer to it with {@code @SecurityRequirement}. */
    public static final String BEARER_AUTH = "bearerAuth";

    /** The version comes from pom.xml (via {@code app.version} in application.properties), so the docs always match the build. */
    @Bean
    public OpenAPI restaurantDealsOpenApi(@Value("${app.version}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("Restaurant Deals API")
                        .version(version)
                        .description("Consumes and transforms restaurant deal data from the EatClub restaurant feed."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Get a token from POST /oauth2/token, then paste it here.")));
    }
}
