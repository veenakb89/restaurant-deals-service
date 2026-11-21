# Restaurant Deals Service

A Spring Boot API that reads restaurant deal data from
<https://eccdn.com.au/misc/challengedata.json> and transforms it.

- **Task 1:** `GET /api/deals?timeOfDay=…` returns the deals active at a given time.

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

### `GET /api/deals?timeOfDay={time}`

`timeOfDay` can be a 12-hour time (`3:00pm`, `3pm`, `10:30am`) or a 24-hour time (`10:30`, `15:00`). Case and whitespace don't matter.

```bash
curl "http://localhost:8080/api/deals?timeOfDay=6:00pm"
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

### Errors

Every error returns the same JSON body (`application/json`), and the HTTP status is set on the response:

```json
{
  "error": {
    "type": "invalid_request_error",
    "code": "invalid_time_of_day",
    "message": "Invalid time of day: '25:00'. Expected a time such as '3:00pm', '3pm' or '15:00'.",
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
| Upstream data source unreachable, failing or returning invalid data | `502 Bad Gateway` | `api_error` | `feed_unavailable` |
| Unexpected server error (no internal details are exposed) | `500 Internal Server Error` | `api_error` | `internal_error` |

## Results for the requested test cases

| `timeOfDay` | Deals | Restaurants |
|---|---|---|
| `3:00pm` | 5 | Masala Kitchen ×2, ABC Chicken ×2, Kekou ×1 (the 2pm–9pm deal) |
| `6:00pm` | 9 | Masala Kitchen ×2, ABC Chicken ×2, Vrindavan ×1, Kekou ×2, Gyoza Gyoza ×2 |
| `9:00pm` | 4 | ABC Chicken ×2, Gyoza Gyoza ×2 |

## Assumptions

1. **A deal with no hours of its own runs during the restaurant's hours.** The source uses `open`/`close` on some deals, `start`/`end` on others, and nothing on the rest. Both spellings are accepted.
2. **A deal is only available while the restaurant is open.** Each deal's window is intersected with the restaurant's hours. For example, Vrindavan's deal runs 3pm–9pm, but the restaurant opens at 6pm, so the deal is active 6pm–9pm.
3. **Windows include the start time but not the end time.** A restaurant that opens at 3:00pm is open at 3:00pm. One that closes at 3:00pm is closed at 3:00pm. This is why OzzyThai (8am–3pm) isn't in the 3:00pm results, and Masala Kitchen (3pm–9pm) isn't in the 9:00pm results. Windows that cross midnight (e.g. 10pm–2am) are supported.
4. **Field values are passed through unchanged as strings**, matching the response contract. The field name `restarantSuburb` is misspelled on purpose, because that's how the challenge's contract spells it.
5. **Malformed records are skipped and logged.** One deal with unparseable hours doesn't fail the whole request.
6. **Data is fetched fresh on every request**, so results always reflect the source. In production I'd add a short-TTL cache (e.g. Caffeine) in front of `RestaurantFeedClient`.

## Project structure

```
src/main/java/com/eatclub/deals
├── controller/     REST endpoints (DealController)
├── dto/            API response objects (ActiveDealsResponse, ActiveDeal, ApiErrorResponse, ErrorDetail)
├── service/        Business logic (DealService)
├── client/         Fetches the upstream restaurant feed (RestaurantFeedClient)
│   └── model/      Shape of the feed's JSON (RestaurantFeed, Restaurant, Deal)
├── exception/      Custom exceptions, error codes and types (ErrorCode, ErrorType) and the global error handler (GlobalExceptionHandler)
├── config/         Spring configuration (OpenAPI/Swagger)
└── time/           Time parsing (TimeOfDayParser) and opening-hours windows (TimeWindow)
```

The upstream JSON models (`client/model`) are kept separate from the API's response objects (`dto`), so a change in the upstream format doesn't leak into this API's contract.
