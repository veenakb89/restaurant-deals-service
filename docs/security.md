# Security

The deals endpoints (`/api/v1/**`) need a JWT access token, sent as `Authorization: Bearer <token>`. The token endpoint, Swagger UI, the API docs and the health checks are public.

## Flow

1. **Get a token:** `POST /oauth2/token` (no parameters) returns a JWT signed with HS256 (HMAC-SHA256 with a shared secret). It's valid for 1 hour.

   ```json
   {
     "access_token": "eyJhbGciOiJIUzI1NiJ9...",
     "token_type": "Bearer",
     "expires_in": 3600,
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

**In Swagger UI:** call `POST /oauth2/token`, copy `access_token`, click **Authorize** and paste it. The deals endpoints then send it automatically.

## Questions and answers

**Q: Which endpoints need an access token?**
A: Both deals endpoints (everything under `/api/`), and the Actuator endpoints other than health (e.g. `/actuator/metrics`, which accepts any valid token). `POST /oauth2/token`, Swagger UI, `/v3/api-docs` and `/actuator/health/**` are public.

**Q: What makes a token invalid?**
A: A missing token, or one that isn't a JWT, has been changed, is signed with a different secret or algorithm, has expired, or has the wrong issuer or audience. All of these return `401` with code `unauthorized`. A valid token without the `deals:read` scope returns `403` with code `forbidden`.

**Q: Why is a missing scope `403` and not `401`?**
A: `401` means "who are you?" (the authentication failed); `403` means "I know who you are, but you're not allowed to do this". A token without the scope passes every authentication check, so it's `403`, as RFC 6750 specifies for `insufficient_scope`.

## Configuration

The token's issuer, audience, subject, scope and lifetime are the `security.jwt.*` settings in `application.properties`. `security.jwt.scope` (default `deals:read`) is read in both places that need it: the issuer puts it in tokens, and the deals endpoints require it. The Swagger descriptions mention the default `deals:read` as plain text, so update them if you change it.

The default signing secret is for local runs only. Set the `JWT_SECRET` environment variable (32+ characters) anywhere else; ideally it comes from a secrets manager.

## Demo shortcuts, and what production would do instead

- **No client check.** The token endpoint doesn't check who is asking, so anyone who can reach it gets a token. In production, an identity provider (IdP) issues tokens only after the client authenticates (e.g. with OAuth2 client credentials), and this app's `security/issuer` package is removed.
- **A shared secret.** With HS256, whoever can verify a token can also create one. That's fine here, because this app both issues and checks the tokens. IdPs sign with a private key (RS256) and publish the public keys (JWKS), so APIs can verify tokens without being able to create them. Switching to an IdP is a configuration change: `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- **No early revocation.** A token stays valid until it expires, which is why its lifetime is short.
