package com.eatclub.deals.security.issuer;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** An OAuth2-style access token response; the field names follow the OAuth2 spec (snake_case). */
@Schema(description = "An access token for the deals API")
public record TokenResponse(

        @Schema(description = "The signed JWT to send as `Authorization: Bearer <token>`", example = "eyJhbGciOiJIUzI1NiJ9...")
        @JsonProperty("access_token") String accessToken,

        @Schema(description = "Always `Bearer`", example = "Bearer")
        @JsonProperty("token_type") String tokenType,

        @Schema(description = "Seconds until the token expires", example = "43200")
        @JsonProperty("expires_in") long expiresIn,

        @Schema(description = "What the token allows", example = "deals:read")
        String scope) {
}
