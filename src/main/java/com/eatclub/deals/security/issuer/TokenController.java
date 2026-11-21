package com.eatclub.deals.security.issuer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo token issuer: {@code POST /oauth2/token}. */
@RestController
@Tag(name = "Auth", description = "Access tokens (demo issuer)")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping(value = "/oauth2/token", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Issue an access token",
            description = "Returns a JWT signed with HS256 for the configured demo client. Demo only: it takes no "
                    + "client credentials. In production, tokens come from an identity provider.")
    @ApiResponse(responseCode = "200", description = "A new access token")
    public TokenResponse issueToken() {
        return tokenService.issueToken();
    }
}
