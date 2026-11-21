package com.eatclub.deals.security;

import com.eatclub.deals.security.issuer.TokenService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Makes every MockMvc request carry a valid access token from the app's own issuer, so tests of the deals API
 * don't need to add the header themselves. Import it with {@code @Import(BearerTokenMockMvc.class)}.
 */
@TestConfiguration
public class BearerTokenMockMvc {

    @Bean
    MockMvcBuilderCustomizer bearerTokenOnEveryRequest(TokenService tokenService) {
        String header = "Bearer " + tokenService.issueToken().accessToken();
        return builder -> builder.defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, header));
    }
}
