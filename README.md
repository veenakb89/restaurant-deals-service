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

`timeOfDay` can be a 12-hour time with `am`/`pm` and no space (`3:00pm`, `10:30AM`) or a 24-hour time (`10:30`, `15:00`). Hour and minutes are always separated by `:`. Leading and trailing spaces are ignored.

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
| Upstream data source unreachable, failing or returning invalid data | `502 Bad Gateway` | `api_error` | `feed_unavailable` |
| Unexpected server error (no internal details are exposed) | `500 Internal Server Error` | `api_error` | `internal_error` |


## Business rules: questions and answers

Clarify these with the product owner.

### When is a deal active?

A deal is only ever active while its restaurant is open. When a deal gives only `open` or only `close`, the missing side comes from the restaurant: no `open` means the restaurant's opening time, and no `close` means its closing time. A blank value counts as missing.

Partial times are read on the day the restaurant opens. A time after midnight only counts as the next day while the restaurant is still open; any other time when the restaurant is closed counts as before opening.

| Question | Input | Expected output | Explanation |
|---|---|---|---|
| A deal has no hours of its own. When is it active? | Masala Kitchen 3pm–9pm, its second deal has no hours | Active 3pm–9pm | The deal uses its restaurant's hours. |
| Some deals use `start`/`end`, others `open`/`close`. Are they handled differently? | Kekou deal `start` 2pm, `end` 9pm | Active 2pm–9pm | No. Both spellings mean the deal's hours. |
| A deal starts before its restaurant opens. When is it active? | Vrindavan 6pm–9pm, deal 3pm–9pm | Active 6pm–9pm | It starts when the restaurant opens. |
| A deal ends after its restaurant closes. When is it active? | Restaurant 9am–10pm, deal 5pm–11pm | Active 5pm–10pm | It stops when the restaurant closes. |
| A deal's hours are entirely outside its restaurant's hours. Is it active? | Restaurant 9am–5pm, deal 6pm–8pm | Never active | The restaurant is closed for the whole deal. |
| A deal has `open` but no `close`. When is it active? | Restaurant 9am–10pm, deal `open` 6pm | Active 6pm–10pm | Missing `close` → the restaurant's closing time. |
| A deal has `close` but no `open`. When is it active? | Restaurant 9am–10pm, deal `close` 2pm | Active 9am–2pm | Missing `open` → the restaurant's opening time. |
| A deal's `open` is blank. When is it active? | Restaurant 9am–10pm, deal `open` " ", `close` 2pm | Active 9am–2pm | A blank value counts as missing. |
| A deal's only time is before the restaurant opens. | Restaurant 9am–10pm, deal `open` 7am | Active 9am–10pm | It starts when the restaurant opens. |
| A deal's only time is after the restaurant closes. | Restaurant 9am–10pm, deal `close` 11pm | Active 9am–10pm | It stops when the restaurant closes. |
| A deal's `open` is at or after closing time. | Restaurant 9am–10pm, deal `open` 10pm or 11pm | Never active | The restaurant has already closed. |
| A deal's `close` is at or before opening time. | Restaurant 9am–10pm, deal `close` 9am or 8am | Never active | The deal ends before the restaurant opens. |
| A partial deal at a restaurant open past midnight (`open` before midnight). | Restaurant 6pm–2am, deal `open` 11pm | Active 11pm–2am | Runs past midnight until the restaurant closes. |
| A partial deal at a restaurant open past midnight (`open` after midnight). | Restaurant 6pm–2am, deal `open` 1am | Active 1am–2am | 1am counts as the next day, because the restaurant is still open then. |
| A partial deal at a restaurant open past midnight (`close` after midnight). | Restaurant 6pm–2am, deal `close` 1am | Active 6pm–1am | Runs from opening until 1am the next day. |
| A partial deal whose `open` is while a late-night restaurant is closed. | Restaurant 6pm–2am, deal `open` 3am | Active 6pm–2am | 3am counts as before the 6pm opening, so it starts at opening. |
| A partial deal whose `close` is while a late-night restaurant is closed. | Restaurant 6pm–2am, deal `close` 3am | Never active | 3am counts as before the 6pm opening, so it ends before the restaurant opens. |
| A restaurant has no opening hours, or blank ones. Is it open? | Restaurant with no `open`/`close`, deal with no hours | Active all day | The restaurant is treated as open all day. |
| A restaurant has only an opening time. Is it open? | Restaurant `open` 9am, no `close` | Active all day | Incomplete restaurant hours count as no hours. |
| A partial deal at a restaurant with no hours (`open` only). | Restaurant with no hours, deal `open` 6pm | Active 6pm–midnight | "All day" means midnight to midnight. |
| A partial deal at a restaurant with no hours (`close` only). | Restaurant with no hours, deal `close` 2pm | Active midnight–2pm | Runs from midnight until 2pm. |
| A partial deal at a restaurant with no hours (`close` 12:00am). | Restaurant with no hours, deal `close` 12:00am | Active all day | Until midnight is the whole day. |
| A partial deal time can't be read. | Restaurant 9am–5pm, deal `open` "abc" | Not returned; a warning is logged | Same as any malformed deal hours. |

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
A: The request returns `502 Bad Gateway` with code `feed_unavailable` and the message "Deal data source is currently unavailable." No partial or old data is returned. The connection timeout is 5 seconds and the read timeout is 10 seconds, both set in `application.properties`.

**Q: The data source returns an empty response body. What happens?**
A: It's treated as a feed with no restaurants, so the response is `200` with an empty `deals` list.

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
