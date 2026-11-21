package com.eatclub.deals.security;

import com.eatclub.deals.TestFeeds;
import com.eatclub.deals.client.RestaurantFeedClient;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The deals API needs a valid bearer token; the token endpoint and the API docs stay public. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ApiAuthorizationTest {

    private static final String DEALS = "/api/v1/deals?timeOfDay=6:00pm";
    private static final String PEAK_TIME = "/api/v1/deals/peak-time";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties properties;

    @MockitoBean
    private RestaurantFeedClient client;

    @BeforeEach
    void setUp() {
        when(client.fetch()).thenReturn(TestFeeds.challengeData());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {DEALS, PEAK_TIME})
    void tokenFromTheTokenEndpointIsAccepted(String url) throws Exception {
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFromTheTokenEndpoint()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {DEALS, PEAK_TIME})
    void requestWithoutATokenIsUnauthorized(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("unauthorized"))
                .andExpect(jsonPath("$.error.message")
                        .value("A valid access token is required: send 'Authorization: Bearer <token>'."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"junk", "tampered", "wrong secret", "expired", "wrong issuer", "wrong audience",
            "no audience", "basic scheme"})
    void invalidTokenIsUnauthorized(String problem) throws Exception {
        mockMvc.perform(get(DEALS).header(HttpHeaders.AUTHORIZATION, authorizationHeaderWith(problem)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void tokenWithoutTheDealsScopeIsForbidden() throws Exception {
        String token = signedToken(properties.secret(), claims -> claims.claim("scope", "other:read"));

        mockMvc.perform(get(DEALS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("forbidden"))
                .andExpect(jsonPath("$.error.message").value("The access token does not allow this request."));
    }

    @Test
    void rejectedTokensAreLoggedWithTheReasonButNeverTheToken(CapturedOutput output) throws Exception {
        String token = signedToken("some-other-secret-that-is-32-chars-long", claims -> { });

        mockMvc.perform(get(PEAK_TIME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        assertThat(output).contains("Unauthorized request to GET /api/v1/deals/peak-time");
        assertThat(output).doesNotContain(token);
    }

    @Test
    void tokenEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/oauth2/token")).andExpect(status().isOk());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private String tokenFromTheTokenEndpoint() throws Exception {
        String body = mockMvc.perform(post("/oauth2/token")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }

    /** An {@code Authorization} header value that must be rejected, for each kind of problem. */
    private String authorizationHeaderWith(String problem) {
        String secret = properties.secret();
        return switch (problem) {
            case "junk" -> "Bearer not-a-jwt";
            case "tampered" -> "Bearer " + tampered(signedToken(secret, claims -> { }));
            case "wrong secret" -> "Bearer " + signedToken("some-other-secret-that-is-32-chars-long", claims -> { });
            case "expired" -> "Bearer " + signedToken(secret, claims -> claims
                    .issuedAt(Instant.now().minus(Duration.ofHours(3)))
                    .expiresAt(Instant.now().minus(Duration.ofHours(2))));
            case "wrong issuer" -> "Bearer " + signedToken(secret, claims -> claims.issuer("someone-else"));
            case "wrong audience" -> "Bearer " + signedToken(secret, claims -> claims.audience(List.of("another-api")));
            case "no audience" -> "Bearer " + signedToken(secret, claims -> claims.claims(c -> c.remove("aud")));
            case "basic scheme" -> "Basic ZGVtbzpkZW1v";
            default -> throw new IllegalArgumentException(problem);
        };
    }

    /** A token with valid claims (as the app issues them), changed by {@code changes}, signed with {@code secret}. */
    private String signedToken(String secret, Consumer<JwtClaimsSet.Builder> changes) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(properties.subject())
                .audience(List.of(properties.audience()))
                .claim("scope", properties.scope())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)));
        changes.accept(claims);
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    /** Changes the payload (subject → admin) but keeps the original signature. */
    private static String tampered(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("demo-client", "admin");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + forged + "." + parts[2];
    }
}
