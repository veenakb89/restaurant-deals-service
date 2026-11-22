# Testing

```bash
./mvnw test                                # unit tests only (fast, no Spring app)
./mvnw verify                              # unit tests, then integration tests
```

The tests use a saved copy of the feed (`src/test/resources/challengedata.json`), so they need no network access.

## Results for the challenge test cases

These are the results with the current feed. They aren't just written down here: `ActiveDealsApiIT` compares the whole response at each time with `src/test/resources/expected/deals-at-{3pm,6pm,9pm}.json` (every field, value and the order of the deals), and `PeakTimeApiIT` checks the peak window, so a change in behaviour fails the build.

### `GET /api/v1/deals?timeOfDay=…`

A restaurant's opening time is included and its closing time is not, so a restaurant that closes at 9:00pm isn't in the 9:00pm results.

| `timeOfDay` | Deals | Restaurants (number of deals) |
|---|---|---|
| `3:00pm` | 5 | Masala Kitchen (2), ABC Chicken (2), Kekou (1) |
| `6:00pm` | 9 | Masala Kitchen (2), ABC Chicken (2), Vrindavan (1), Kekou (2), Gyoza Gyoza Melbourne Central (2) |
| `9:00pm` | 4 | ABC Chicken (2), Gyoza Gyoza Melbourne Central (2) |

Why some restaurants are missing:
- **3pm:** OzzyThai closes at 3pm. Vrindavan's deal starts at 3pm, but the restaurant only opens at 6pm.
- **9pm:** Masala Kitchen and Vrindavan close at 9pm, and both of Kekou's deals end at 9pm.

### `GET /api/v1/deals/peak-time`

```json
{ "peakTimeStart": "6:00pm", "peakTimeEnd": "9:00pm" }
```

9 deals, from when Vrindavan opens until Masala Kitchen, Vrindavan and Kekou's deals end. How the peak is worked out: [business-rules.md](business-rules.md#peak-time).

## How the tests are organised

**Unit tests** (`*Test`, next to the code they test): one class at a time, with its dependencies mocked or not needed.

| Test | Covers |
|---|---|
| `service/DealServiceTest` | The business rules: which deals are active when, and the peak window |
| `time/` | Parsing and formatting `timeOfDay`, opening-hours windows |
| `controller/DealControllerTest` | The controller alone (`@WebMvcTest`, service mocked): parses `timeOfDay`, calls the service, maps the result |
| `client/` | The feed client against a local HTTP server; retry and circuit breaker |
| Others | Error handler, request logging filter, settings validation |

**Integration tests** (`*IT`, in `integration/`): the whole app (`@SpringBootTest`), with only the upstream feed mocked or stubbed.

| Test | Covers |
|---|---|
| `api/ActiveDealsApiIT` | The strict JSON contract at 3pm, 6pm and 9pm; every accepted and rejected `timeOfDay` |
| `api/PeakTimeApiIT` | The peak-time response and its edge cases (no deals, all day) |
| `api/ApiErrorResponsesIT` | 404, 405, 502 and 500 on both endpoints |
| `security/ApiAuthorizationIT`, `security/TokenEndpointIT` | Real tokens against the real security rules |
| `client/FeedCacheIT` | The real feed client with its cache, against a local HTTP server |
| `actuator/ActuatorIT` | Health checks and metrics |
