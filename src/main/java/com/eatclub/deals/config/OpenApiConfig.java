package com.eatclub.deals.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Restaurant Deals API",
        version = "1.0",
        description = "Consumes and transforms restaurant deal data from the EatClub restaurant feed."))
public class OpenApiConfig {
}
