# Security

The deals endpoints (`/api/v1/**`) need a JWT access token with the `deals:read` scope, sent as `Authorization: Bearer <token>`. `/actuator/metrics` accepts any valid token. How to get a token and use it in Swagger UI is in the README's Quick start.

## Flow

1. **Get a token:** `POST /oauth2/token` (no parameters) returns a JWT. It's valid for 12 hours.

   ```json
   {
     "access_token": "eyJhbGciOiJIUzI1NiJ9...",
     "token_type": "Bearer",
     "expires_in": 43200,
     "scope": "deals:read"
   }
   ```

2. **Call the API** with the token in the `Authorization` header.
3. **The API checks the token** with Spring Security's OAuth2 resource-server support. It accepts the token only if:
   - it's signed with our secret, using HS256 and no other algorithm;
   - it hasn't expired (60 seconds of clock difference allowed);
   - its issuer is `restaurant-deals` and its audience includes `deals-api`;
   - its scope includes `deals:read`.

   Otherwise the response is `401 unauthorized`, or `403 forbidden` if only the scope is missing, in the usual [error format](errors.md).


## Configuration

The settings are the `security.jwt.*` rows in the README's Configuration table. Two things to know:
- **The scope is set in one place:** `security.jwt.scope`. Tokens get it, and the API requires it. If you change it, also update the Swagger text, which mentions `deals:read`.
- **The default signing secret is for local runs only.** Set `JWT_SECRET` (32+ characters) anywhere else, ideally from a secrets manager.

## Demo shortcuts, and what production would do instead

- **No client check.** The token endpoint doesn't check who is asking, so anyone who can reach it gets a token. In production, an identity provider (IdP) issues tokens only after the client authenticates (e.g. with OAuth2 client credentials), and this app's `security/issuer` package is removed.
- **A shared secret.** With HS256, whoever can verify a token can also create one. That's fine here, because this app both issues and checks the tokens. IdPs sign with a private key (RS256) and publish the public keys (JWKS), so APIs can verify tokens without being able to create them. Switching to an IdP is a configuration change: `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- **No early revocation, and a long lifetime.** A token stays valid until it expires. The 12-hour lifetime keeps the demo convenient: one token lasts a whole review session.
