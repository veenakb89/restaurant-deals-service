package com.eatclub.deals.security.issuer;

import com.eatclub.deals.client.RestaurantFeedClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder appDecoder; // the decoder the API uses: signature, expiry, issuer and audience

    @MockitoBean
    private RestaurantFeedClient client; // not used; avoids calling the real feed

    @Test
    void returnsABearerTokenResponse() throws Exception {
        mockMvc.perform(post("/oauth2/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(43200))
                .andExpect(jsonPath("$.scope").value("deals:read"))
                .andExpect(jsonPath("$.access_token").isString());
    }

    @Test
    void tokenIsSignedWithHs256AndCarriesTheConfiguredClaims() throws Exception {
        Jwt jwt = appDecoder.decode(issueToken());

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("restaurant-deals");
        assertThat(jwt.getSubject()).isEqualTo("demo-client");
        assertThat(jwt.getAudience()).containsExactly("deals-api");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("deals:read");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void tokenIsRejectedWhenCheckedWithADifferentSecret() throws Exception {
        String token = issueToken();
        var otherDecoder = decoderFor("a-completely-different-secret-of-32-chars");

        assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void getIsNotAllowed() throws Exception {
        mockMvc.perform(get("/oauth2/token"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("method_not_allowed"));
    }

    private String issueToken() throws Exception {
        String body = mockMvc.perform(post("/oauth2/token")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }

    /** A decoder like the app's, but with a secret of our choosing. */
    private static NimbusJwtDecoder decoderFor(String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
