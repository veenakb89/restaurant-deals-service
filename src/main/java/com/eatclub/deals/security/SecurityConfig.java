package com.eatclub.deals.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * The deals API ({@code /api/**}) needs a valid JWT bearer token with the configured scope
 * ({@code security.jwt.scope}, {@code deals:read} by default).
 * Everything else (the demo token endpoint and Swagger) is public. The API is stateless: no sessions, cookies or
 * login forms, so CSRF protection isn't needed.
 */
@Configuration
public class SecurityConfig {

    /** Spring Security turns each scope in the token into an authority with this prefix. */
    private static final String SCOPE_PREFIX = "SCOPE_";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JsonSecurityErrorHandler errorHandler, JwtProperties properties) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**").hasAuthority(SCOPE_PREFIX + properties.scope())
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(withDefaults())
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .build();
    }
}
