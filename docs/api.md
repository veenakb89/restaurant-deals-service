# API

The request and response contract for both deals endpoints. The live reference is Swagger UI at <http://localhost:8080/swagger-ui.html> (OpenAPI spec at `/v3/api-docs`). Both endpoints need a bearer token ([security.md](security.md)); errors use one format ([errors.md](errors.md)). How deals and the peak are decided is in [business-rules.md](business-rules.md).

## `GET /api/v1/deals?timeOfDay={time}`

Returns the deals active at that time.

### `timeOfDay`

Required. Leading and trailing spaces are trimmed, then the value must be one of:
- **12-hour:** hour 1–12, `:`, two-digit minutes, then `am`/`pm` with no space, in any case: `3:00pm`, `3:00PM`, `10:30am`. `12:00am` is midnight and `12:00pm` is noon.
- **24-hour:** hour 0–23, `:`, two-digit minutes, no suffix: `15:00`, `09:05`, `9:05`, `00:00`.

Anything else returns `400 invalid_time_of_day`, for example:
- no minutes: `3pm`, `15`;
- a space before `am`/`pm`: `3:00 pm`;
- dots or a single letter: `3:00p.m.`, `3:00p`;
- a separator other than `:`, or spaces around it: `3.00pm`, `3 :00pm`;
- single-digit minutes or seconds: `3:0pm`, `3:00:00`;
- out of range: `25:00`, `24:00`, `13:00pm`, `0:00am`, `10:60`;
- empty, blank, or words like `noon`.

A request without `timeOfDay` returns `400 missing_parameter`.

### Response

`GET /api/v1/deals?timeOfDay=6:00pm` (first deal shown):

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

- **Order:** as in the feed, restaurant by restaurant, with each restaurant's deals in order. No sorting.
- **Nothing active:** `200` with `{"deals": []}`, e.g. at `4:00am`.
- **`restarantSuburb`** is misspelled on purpose, to match the response contract.

## `GET /api/v1/deals/peak-time`

Returns the time window with the most active deals. It takes no parameters.

```json
{ "peakTimeStart": "6:00pm", "peakTimeEnd": "9:00pm" }
```

- The start is inclusive and the end exclusive.
- Times use the feed's format (12-hour, lowercase `am`/`pm`), so they can be passed straight to `GET /api/v1/deals` as `timeOfDay`.
- Both fields are `null` when no deal is ever active, and `12:00am`–`12:00am` means all day. Ties and the window length are explained in [business-rules.md](business-rules.md#peak-time).

## Versioning

The version is in the path (`/api/v1`). A breaking change would be published as `/api/v2` alongside v1, so existing clients keep working while they move over. Additive changes, such as a new optional field, stay in v1.
