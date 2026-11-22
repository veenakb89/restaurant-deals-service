# Errors

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

## Error codes

| Case | Status | `type` | `code` |
|---|---|---|---|
| `timeOfDay` is not a valid time | `400 Bad Request` | `invalid_request_error` | `invalid_time_of_day` |
| A required parameter is missing | `400 Bad Request` | `invalid_request_error` | `missing_parameter` |
| Any other malformed request | `4xx` | `invalid_request_error` | `bad_request` |
| Access token missing, invalid or expired | `401 Unauthorized` | `invalid_request_error` | `unauthorized` |
| Access token without the required scope | `403 Forbidden` | `invalid_request_error` | `forbidden` |
| Unknown URL (including the old unversioned `/api/deals`) | `404 Not Found` | `invalid_request_error` | `not_found` |
| Wrong HTTP method | `405 Method Not Allowed` | `invalid_request_error` | `method_not_allowed` |
| Upstream data source unreachable, failing or returning invalid data | `502 Bad Gateway` | `api_error` | `feed_unavailable` |
| Unexpected server error (no internal details are exposed) | `500 Internal Server Error` | `api_error` | `internal_error` |

## Notes

- **One place:** all errors, including Spring's own (missing parameter, unknown URL, wrong method), go through one global handler (`GlobalExceptionHandler`). Security errors (401/403) are written by `JsonSecurityErrorHandler`, using the same body.
- **Docs match responses:** the Swagger schema for errors is the real response class, and a strict JSON test checks the response format.
- **401 before 404:** under `/api/`, a request without a token gets `401` even for an unknown URL, so the API doesn't reveal which URLs exist. With a token, it's `404`.
