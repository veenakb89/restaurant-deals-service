package com.eatclub.deals.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for the JWTs this app issues and accepts, from the {@code security.jwt.*} properties.
 * {@code scope} is both put in issued tokens and required by the deals endpoints.
 * The app refuses to start if a value is blank or the secret is shorter than 32 characters (HS256 needs 256 bits).
 */
@Validated
@ConfigurationProperties("security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String subject,
        @NotBlank String scope,
        @NotBlank @Size(min = 32) String secret,
        @DefaultValue("12h") Duration tokenTtl) {
}
