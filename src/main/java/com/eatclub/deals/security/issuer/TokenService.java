package com.eatclub.deals.security.issuer;

import com.eatclub.deals.security.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Issues JWTs signed with HS256. Every claim comes from {@link JwtProperties}.
 * <p>
 * Demo only: there's no client authentication, so anyone who can reach the endpoint gets a token. In production,
 * an identity provider issues tokens after the client proves who it is, and this package is removed.
 */
@Service
public class TokenService {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issueToken() {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims())).getTokenValue();
        return new TokenResponse(token, TOKEN_TYPE, properties.tokenTtl().toSeconds(), properties.scope());
    }

    private JwtClaimsSet claims() {
        Instant now = clock.instant();
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(properties.subject())
                .audience(List.of(properties.audience()))
                .claim("scope", properties.scope())
                .issuedAt(now)
                .expiresAt(now.plus(properties.tokenTtl()))
                .build();
    }
}
