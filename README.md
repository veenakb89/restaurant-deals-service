# Restaurant Deals Service

A Spring Boot API that reads restaurant deal data from
<https://eccdn.com.au/misc/challengedata.json> and transforms it.

- **Task 1:** `GET /api/deals?timeOfDay=…` returns the deals active at a given time.
- **Task 2:** `GET /api/deals/peak-time` returns the time window during which the most deals are active.
- **Auth (demo):** `POST /oauth2/token` issues a signed JWT access token.

## Tech stack

- Java 21, Spring Boot 4
- springdoc-openapi (Swagger UI)
- JUnit 5, AssertJ, Mockito

## Requirements

- Java 21+
- No Maven install needed: the Maven Wrapper (`mvnw`) is included.

## Run

```bash
./mvnw spring-boot:run          # macOS / Linux
mvnw.cmd spring-boot:run        # Windows
```

The service starts on `http://localhost:8080`.

Interactive API docs (Swagger UI): <http://localhost:8080/swagger-ui.html>.
The OpenAPI spec is at <http://localhost:8080/v3/api-docs>.

## Test

```bash
./mvnw test                     # macOS / Linux
mvnw.cmd test                   # Windows
```

The tests use a snapshot of the restaurant feed (`src/test/resources/challengedata.json`), so they don't need network access.

## API

Both deals endpoints need an access token (see [Security](#security)). Get one first:

```bash
TOKEN=$(curl -s -X POST "http://localhost:8080/oauth2/token" | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
```

### `GET /api/deals?timeOfDay={time}`

`timeOfDay` can be a 12-hour time with `am`/`pm` and no space (`3:00pm`, `10:30AM`) or a 24-hour time (`10:30`, `15:00`). Hour and minutes are always separated by `:`. Leading and trailing spaces are ignored.

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/deals?timeOfDay=6:00pm"
```

```json
{
  "deals": [
    {
      "restaurantObjectId": "DEA567C5-F64C-3C03-FF00-E3B24909BE00",
      "restaurantName": "Masala Kitchen",
      "restaurantAddress1": "55 Walsh Street",
      "restarantSuburb": "Lower East",
      "restaurantOpen": "3:00pm",
      "restaurantClose": "9:00pm",
      "dealObjectId": "DEA567C5-0000-3C03-FF00-E3B24909BE00",
      "discount": "50",
      "dineIn": "false",
      "lightning": "true",
      "qtyLeft": "5"
    }
  ]
}
```

### `GET /api/deals/peak-time`

Takes no parameters. Returns the window during which the most deals are active. The start is inclusive and the end exclusive, and times use the same format as the feed.

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/deals/peak-time"
```

```json
{
  "peakTimeStart": "6:00pm",
  "peakTimeEnd": "9:00pm"
}
```

### `POST /oauth2/token`

Takes no parameters. Returns a JWT signed with HS256 (HMAC-SHA256 with a shared secret) for the configured demo client. The claims (`iss`, `sub`, `aud`, `scope`, and the lifetime `exp` − `iat`) come from the `security.jwt.*` settings in `application.properties`.

```bash
curl -X POST "http://localhost:8080/oauth2/token"
```

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "deals:read"
}
```

This is a demo issuer; see [Security](#security).

### Errors

Every error returns the same JSON body (`application/json`), and the HTTP status is set on the response:

```json
{
  "error": {
    "type": "invalid_request_error",
    "code": "invalid_time_of_day",
    "message": "Invalid time of day: '25:00'. Expected a time such as '3:00pm', '10:30AM' or '15:00'.",
    "param": "timeOfDay"
  }
}
```

| Field | Meaning |
|---|---|
| `type` | Broad category: `invalid_request_error` (a problem with the request) or `api_error` (a problem on the server side) |
| `code` | Stable, machine-readable error code. Clients should rely on `code`, not on `message`, which may change. |
| `message` | Human-readable explanation |
| `param` | The request parameter that caused the error. Only present for parameter errors. |

| Case | Status | `type` | `code` |
|---|---|---|---|
| `timeOfDay` is not a valid time | `400 Bad Request` | `invalid_request_error` | `invalid_time_of_day` |
| A required parameter is missing | `400 Bad Request` | `invalid_request_error` | `missing_parameter` |
| Any other malformed request | `4xx` | `invalid_request_error` | `bad_request` |
| Unknown URL | `404 Not Found` | `invalid_request_error` | `not_found` |
| Wrong HTTP method | `405 Method Not Allowed` | `invalid_request_error` | `method_not_allowed` |
| Access token missing, invalid or expired | `401 Unauthorized` | `invalid_request_error` | `unauthorized` |
| Access token without the `deals:read` scope | `403 Forbidden` | `invalid_request_error` | `forbidden` |
| Upstream data source unreachable, failing or returning invalid data | `502 Bad Gateway` | `api_error` | `feed_unavailable` |
| Unexpected server error (no internal details are exposed) | `500 Internal Server Error` | `api_error` | `internal_error` |

## Security

The deals endpoints (`/api/**`) need a JWT access token, sent as `Authorization: Bearer <token>`. The token endpoint and the API docs are public.

1. **Get a token:** `POST /oauth2/token` returns a JWT signed with HS256 (HMAC-SHA256 with a shared secret). It's valid for 1 hour.
2. **Call the API** with the token in the `Authorization` header.
3. **The API checks the token** with Spring Security's OAuth2 resource-server support. It accepts the token only if:
   - it's signed with our secret, using HS256 and no other algorithm;
   - it hasn't expired (60 seconds of clock difference allowed);
   - its issuer is `restaurant-deals` and its audience includes `deals-api`;
   - its scope includes `deals:read`.

   Otherwise the response is `401 unauthorized`, or `403 forbidden` if only the scope is missing, in the usual error format. The standard `WWW-Authenticate: Bearer ...` header says why a token was rejected.

**In Swagger UI:** call `POST /oauth2/token`, copy `access_token`, click **Authorize** and paste it. The deals endpoints then send it automatically.

**Configuration:** the token's issuer, audience, subject, scope and lifetime are the `security.jwt.*` settings in `application.properties`. `security.jwt.scope` (default `deals:read`) is read in both places that need it: the issuer puts it in tokens, and the deals endpoints require it. The Swagger descriptions mention the default `deals:read` as plain text, so update them if you change it. The default signing secret is for local runs only. Set the `JWT_SECRET` environment variable (32+ characters) anywhere else; ideally it comes from a secrets manager.

**Demo shortcuts, and what production would do instead:**
- **No client check.** The token endpoint doesn't check who is asking, so anyone who can reach it gets a token. In production, an identity provider (IdP) issues tokens only after the client authenticates (e.g. with OAuth2 client credentials), and this app's `security/issuer` package is removed.
- **A shared secret.** With HS256, whoever can verify a token can also create one. That's fine here, because this app both issues and checks the tokens. IdPs sign with a private key (RS256) and publish the public keys (JWKS), so APIs can verify tokens without being able to create them. Switching to an IdP is a configuration change: `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- **No early revocation.** A token stays valid until it expires, which is why its lifetime is short.

## Logging

- **Request ID:** every request gets an ID, returned in the `X-Request-Id` response header. A caller can send its own ID (letters, digits, `-` or `_`, up to 64 characters) to have it reused. Every log line written while handling the request starts with the ID, e.g. `[3f2a9c1e-...]`, so all the lines for one request can be found together.
- **One summary line per request:** e.g. `GET /api/deals/peak-time -> 200 in 342 ms`. Swagger UI and the API docs are left out.
- **Warnings:**
  - each feed retry, and the final failure after all attempts;
  - circuit breaker state changes, and requests failing fast while it's open (one line, no stack trace);
  - rejected access tokens, with the reason;
  - skipped or partial deal data.
- **Errors** (with a stack trace): real upstream failures, and unexpected server errors.
- **At startup:** one line with the feed URL, timeouts, retry and circuit-breaker settings.
- **More detail:** set `logging.level.com.eatclub.deals=DEBUG` to also log each upstream feed call, with its duration and the number of restaurants.
- **Never logged:** access tokens, the signing secret, request headers and request bodies.

## Business rules: questions and answers

Clarify these with the product owner.

### When is a deal active?

A deal is only ever active while its restaurant is open. A deal's own hours must have both `open` and `close`, or neither.

| Question | Input | Expected output | Explanation |
|---|---|---|---|
| A deal has no hours of its own. When is it active? | Masala Kitchen 3pm–9pm, its second deal has no hours | Active 3pm–9pm | The deal uses its restaurant's hours. |
| Some deals use `start`/`end`, others `open`/`close`. Are they handled differently? | Kekou deal `start` 2pm, `end` 9pm | Active 2pm–9pm | No. Both spellings mean the deal's hours. |
| A deal starts before its restaurant opens. When is it active? | Vrindavan 6pm–9pm, deal 3pm–9pm | Active 6pm–9pm | It starts when the restaurant opens. |
| A deal ends after its restaurant closes. When is it active? | Restaurant 9am–10pm, deal 5pm–11pm | Active 5pm–10pm | It stops when the restaurant closes. |
| A deal's hours are entirely outside its restaurant's hours. Is it active? | Restaurant 9am–5pm, deal 6pm–8pm | Never active | The restaurant is closed for the whole deal. |
| A deal has only `open` or only `close`, or one of them is blank. When is it active?  | Restaurant 9am–5pm, deal `open` 6pm and no `close` | Active 9am–5pm; a warning is logged | The partial value is ignored and the deal runs for its restaurant's hours, the same as a deal with no hours. It isn't parsed, so even an unreadable value like `"abc"` is ignored. No deal in the current feed has partial hours. |
| A restaurant has no opening hours, or blank ones. Is it open? | Restaurant with no `open`/`close`, deal with no hours | Active all day | The restaurant is treated as open all day. |
| A restaurant has only an opening time. Is it open? | Restaurant `open` 9am, no `close` | Active all day | Incomplete restaurant hours count as no hours. |

Times in "Expected output" include the start and exclude the end: "Active 6pm–10pm" means active at 6:00pm and closed at 10:00pm.

### Times and boundaries

**Q: Is a deal active at exactly its opening time?**
A: Yes. A restaurant open 3pm–9pm is open at 3:00pm. Example: Masala Kitchen (3pm–9pm) is in the 3:00pm results.

**Q: Is a deal active at exactly its closing time?**
A: No. A restaurant open 3pm–9pm is closed at 9:00pm. Examples: OzzyThai (8am–3pm) isn't in the 3:00pm results, and Masala Kitchen isn't in the 9:00pm results.

**Q: How are hours that cross midnight handled, e.g. 6pm–2am?**
A: The window continues past midnight: open from 6:00pm through 1:59am, closed from 2:00am. This works for both restaurant hours and deal hours.

**Q: The opening and closing times are the same, e.g. 3pm–3pm. When is it open?**
A: All day. For a deal, that means it's active for all of its restaurant's opening hours. For a restaurant, it means open around the clock.

**Q: Which date and time zone does `timeOfDay` use?**
A: None. `timeOfDay` is only a time of day, compared directly with the times in the feed, which also have no date or time zone. The day of the week isn't considered.

### Availability

**Q: A deal has `qtyLeft` of `"0"`. Is it returned?**
A: No. Any deal with `qtyLeft` of 0 or less (e.g. `"0"`, `"-1"`) is left out. Surrounding spaces are ignored, so `" 0 "` is also left out. No deal in the current feed has 0, so the test-case results above are unaffected.

**Q: A deal's `qtyLeft` is missing, blank, or not a number (e.g. `"abc"`). Is it returned?**
A: Yes, it's kept. If the value isn't a number, a warning is logged; if it's missing or blank, nothing is logged.

### Requests

**Q: Which `timeOfDay` formats are accepted?**
A: Leading and trailing spaces are trimmed first. The value must then be one of:
- a **12-hour time:** hour 1–12, `:`, two-digit minutes, then `am` or `pm` directly after, with no space. Upper, lower and mixed case all work: `3:00pm`, `3:00PM`, `3:00Pm`, `10:30am`, `10:30AM`. `12:00am` is midnight and `12:00pm` is noon.
- a **24-hour time:** hour 0–23, `:`, two-digit minutes, with no suffix: `10:30`, `09:05`, `9:05`, `15:00`, `00:00`.

**Q: Which `timeOfDay` values are rejected?**
A: Anything else returns `400` with code `invalid_time_of_day` and `param` `timeOfDay`. That includes:
- empty or blank values;
- words like `noon`;
- a 12-hour time without minutes (`3pm`, `12PM`);
- a space before `am`/`pm` (`3:00 pm`, `3:00 PM`);
- `a.m.`/`p.m.` with dots, or just `a`/`p` (`3:00p.m.`, `3:00p`);
- a separator other than `:` (`3.00pm`, `3-00pm`, `15.00`), or spaces around the `:` (`3 :00pm`, `3: 00pm`);
- a bare number with no minutes (`3`, `15`);
- single-digit minutes (`3:0pm`);
- seconds (`3:00:00`);
- out-of-range times (`25:00`, `24:00`, `13:00pm`, `0:00am`, `10:60`).

**Q: Which endpoints need an access token?**
A: Both deals endpoints (everything under `/api/`). `POST /oauth2/token`, Swagger UI and `/v3/api-docs` are public. An unknown `/api/...` URL also returns `401` without a token, so the API doesn't reveal which URLs exist; with a token, it returns `404`.

**Q: What makes a token invalid?**
A: A missing token, or one that isn't a JWT, has been changed, is signed with a different secret or algorithm, has expired, or has the wrong issuer or audience. All of these return `401` with code `unauthorized`. A valid token without the `deals:read` scope returns `403` with code `forbidden`.

**Q: `timeOfDay` isn't sent at all. What happens?**
A: It returns `400` with code `missing_parameter` and `param` `timeOfDay`.

### Responses

**Q: No deals are active at the requested time. What's returned?**
A: `200 OK` with an empty list: `{"deals": []}`. Example: `4:00am`.

**Q: In what order are deals returned?**
A: In the order they appear in the source feed: restaurant by restaurant, and within each restaurant, its deals in order. No sorting is applied.

**Q: Are values changed or cleaned up before they're returned?**
A: No. Every value is returned exactly as the source gives it, as a string. Examples: `"OzzyThai Cafe Bar "` keeps its trailing space, and `"50"`, `"true"` and `"5"` stay strings. The restaurant's own `open`/`close` values are returned as `restaurantOpen`/`restaurantClose`, also unchanged.

**Q: Why is one response field called `restarantSuburb`?**
A: The response contract spells it that way, so the field name is returned exactly as specified.

### Peak time

**Q: How is the peak worked out?**
A: With the same rules as `GET /api/deals`, so asking for any time inside the peak returns the most deals. A deal counts only while both it and its restaurant are open, sold-out deals don't count, and unreadable deals are skipped. The number of active deals can only change at an opening or closing time, so the count is taken at each of those times. Neighbouring stretches with the same highest count are merged into one window. Example: with the current feed the peak is 6:00pm–9:00pm, with 9 deals.

**Q: Are deals or restaurants counted?**
A: Deals. A restaurant with two active deals counts twice. The two deals that share an ID (Kekou and Gyoza Gyoza) count as two.

**Q: Is the end of the peak included?**
A: No, the same as opening hours. "6:00pm–9:00pm" means the peak includes 6:00pm and 8:59pm, but not 9:00pm.

**Q: Several separate windows have the same highest count. Which is returned?**
A: The one that starts earliest in the day, counting from midnight. Example: one deal 9am–11am and one deal 2pm–4pm gives 9:00am–11:00am.

**Q: Can the peak cross midnight?**
A: Yes. Example: a late-night peak is returned as `"11:00pm"`–`"1:00am"`.

**Q: The highest count lasts all day. What's returned?**
A: `"12:00am"`–`"12:00am"`. Equal start and end mean all day, as with opening hours.

**Q: No deal is ever active. What's returned?**
A: `200 OK` with both fields `null`: `{"peakTimeStart": null, "peakTimeEnd": null}`.

**Q: How long is the peak window? Is it a fixed length, e.g. one hour?**
A: No. The data decides the length. The window starts when the number of active deals reaches its highest value and ends when it drops. The current feed gives 3 hours (6:00pm–9:00pm), but the peak could be 1 minute or 24 hours.

Options considered:

| Option | How it works | Typical use |
|---|---|---|
| **Maximum overlap** (chosen) | The window where the most deals are active at the same time, however long it is | Counting things that are active at once, e.g. meeting rooms in use or users online |
| Fixed-length window | The fixed-length window (e.g. 60 minutes) where the most deals are active | Volume metrics, e.g. the traffic "peak hour" or hourly busyness charts |

Why maximum overlap was chosen:
- Deals are time ranges, and "when are the most deals available" asks how many are active at once.
- The response has both a start and an end. With a fixed length, the end would just be the start plus that length.
- The task doesn't give a length, so a fixed length would be an assumption with no basis.
- It's exact. A fixed one-hour window would return 6:00pm–7:00pm and miss 7:00pm–9:00pm, which has the same 9 deals.

Trade-off: a very short peak beats a longer one with slightly fewer deals. For example, 10 deals for 1 minute beats 9 deals for 5 hours.

Possible improvements, if the product owner wants them:
- **Minimum length:** ignore peaks shorter than e.g. 30 minutes.
- **Fixed-length option:** an optional `?duration=60` parameter for "the best 60 minutes". This would change the contract, since the task says the API takes no parameters.
- **More detail:** add the number of deals at the peak, or return every tied window instead of only the earliest. This would also change the contract.
- **Weighting:** count deals by `qtyLeft` or discount instead of counting each deal once.

**Q: What time format does the response use?**
A: The feed's format: 12-hour, two-digit minutes and lowercase `am`/`pm`, e.g. `6:00pm`. The values can be passed straight to `GET /api/deals` as `timeOfDay`.

### Data quality

**Q: A deal has hours that can't be read, e.g. `"not-a-time"`. What happens?**
A: That deal is skipped, a warning is logged, and every other deal is returned as normal.

**Q: A restaurant has opening hours that can't be read. What happens?**
A: All of that restaurant's deals are skipped, with one warning logged per deal. Other restaurants are unaffected.

**Q: A restaurant has no `deals` list, or the feed has no `restaurants` list. What happens?**
A: That restaurant, or the whole feed, contributes no deals. The response is `200` with whatever deals remain, possibly an empty list.

**Q: Two deals have the same `dealObjectId`. Are both returned?**
A: Yes. They're handled as separate deals. Example: Kekou and Gyoza Gyoza each have a deal with ID `B5713CD0-1111-40C7-AFC3-7D46D26B00BF`, with different hours and dine-in settings, and both appear in the 6:00pm results. Each result includes both `restaurantObjectId` and `dealObjectId`, so the two can be told apart.

### Data source

**Q: Is the feed data cached?**
A: No. The feed is fetched from the source URL on every request.

**Q: The data source is unreachable, too slow, returns an error status, or returns invalid JSON. What happens?**
A: The request returns `502 Bad Gateway` with code `feed_unavailable` and the message "Deal data source is currently unavailable." No partial or old data is returned. Before giving up, temporary problems are retried (see the next question). The connection timeout is 2 seconds and the read timeout is 5 seconds.

**Q: Which failures are retried?**
A: Temporary ones: a connection error, a timeout, or an upstream `5xx`. The call is tried up to 3 times, waiting 200ms and then 400ms in between. Upstream `4xx` and invalid JSON aren't retried, because trying again won't fix them. If all attempts fail, the response is `502`, as above. In the worst case (every attempt times out), a request takes about 16 seconds.

**Q: What happens during a longer outage?**
A: A circuit breaker stops the API calling upstream. When at least half of the last 10 calls failed (each retry attempt counts, and at least 5 calls are needed), the circuit **opens** for 30 seconds. Requests then fail immediately with the same `502 feed_unavailable`, instead of waiting for timeouts and retries. After 30 seconds, 2 trial calls are let through. If they succeed, the circuit closes and normal service resumes; if not, it opens again. Each retry and each change of state is logged as a warning.

**Q: Can the timeouts, retry and circuit breaker be changed?**
A: Yes, in `application.properties`: `restaurant-feed.connect-timeout`, `restaurant-feed.read-timeout`, `restaurant-feed.retry.*` and `restaurant-feed.circuit-breaker.*`. Out-of-range values stop the app from starting.

**Q: The data source returns an empty response body. What happens?**
A: It's treated as a feed with no restaurants, so the response is `200` with an empty `deals` list.

## Project structure

```
src/main/java/com/eatclub/deals
├── controller/     REST endpoints: routes and API docs (DealApi), implementation (DealController)
├── dto/            API request/response bodies; currently responses only (ActiveDealsResponse, ActiveDeal, PeakTimeResponse)
├── service/        Business logic (DealService)
├── client/         Fetches the upstream restaurant feed (RestaurantFeedClient), with retry and a circuit breaker
│                   (FeedResilience), and its settings (RestaurantFeedProperties)
│   └── model/      Shape of the feed's JSON (RestaurantFeed, Restaurant, Deal)
├── error/          HTTP error responses: the global error handler (GlobalExceptionHandler), error codes and types
│                   (ErrorCode, ErrorType) and the error body (ApiErrorResponse, ErrorDetail)
├── exception/      Domain exceptions (InvalidTimeOfDayException, FeedUnavailableException, FeedCircuitOpenException)
├── config/         Spring configuration (OpenAPI/Swagger, the app's Clock)
├── logging/        Request ID and one summary log line per request (RequestLoggingFilter)
├── security/       Token checks for /api/** (SecurityConfig), JSON 401/403 errors (JsonSecurityErrorHandler),
│                   JWT settings, signing and validation (JwtProperties, JwtConfig)
│   └── issuer/     Demo token endpoint (TokenController, TokenService, TokenResponse)
└── time/           Time parsing and formatting (TimeOfDayParser, TimeOfDayFormatter) and opening-hours windows (TimeWindow)
```

The upstream JSON models (`client/model`) are kept separate from the API's response objects (`dto`), so a change in the upstream format doesn't leak into this API's contract.
