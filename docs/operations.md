# Operations: the feed, resilience, health and logging

**Contents:** [Upstream feed](#upstream-feed) · [Health and metrics](#health-and-metrics) · [Logging](#logging)

## Upstream feed

Both deals endpoints read the restaurant feed from `restaurant-feed.url`. It's protected by a cache, timeouts, retry and a circuit breaker:

```
request → 60s cache ─(miss)→ retry (3 attempts) → circuit breaker → upstream (2s connect / 5s read)
```

**Q: Is the feed data cached?**
A: Yes, in memory for 60 seconds. The first request fetches the feed, and every request in the next 60 seconds (to either endpoint, for any `timeOfDay`) uses that copy. So a change in the source, such as a deal selling out, can take up to 60 seconds to show.

**Q: The data source is unreachable, too slow, returns an error status, or returns invalid JSON. What happens?**
A: The request returns `502 Bad Gateway` with code `feed_unavailable` and the message "Deal data source is currently unavailable." No partial or old data is returned. Before giving up, temporary problems are retried (see the next question). The connection timeout is 2 seconds and the read timeout is 5 seconds.

**Q: Which failures are retried?**
A: Temporary ones: a connection error, a timeout, or an upstream `5xx`. The call is tried up to 3 times, waiting 200ms and then 400ms in between. Upstream `4xx` and invalid JSON aren't retried, because trying again won't fix them. If all attempts fail, the response is `502`, as above. In the worst case (every attempt times out), a request takes about 16 seconds.

**Q: What happens during a longer outage?**
A: A circuit breaker stops the API calling upstream. When at least half of the last 10 calls failed (each retry attempt counts, and at least 5 calls are needed), the circuit **opens** for 30 seconds. Requests then fail immediately with the same `502 feed_unavailable`, instead of waiting for timeouts and retries. After 30 seconds, 2 trial calls are let through. If they succeed, the circuit closes and normal service resumes; if not, it opens again. Each retry and each change of state is logged as a warning.

All of these values can be changed: see the `restaurant-feed.*` and `spring.cache.*` rows in the README's Configuration table. Out-of-range values stop the app from starting.

## Health and metrics

| Endpoint | Access | Use |
|---|---|---|
| `GET /actuator/health` | Public | Overall status, e.g. `{"status":"UP"}` |
| `GET /actuator/health/liveness` | Public | Is the app running? (e.g. for Kubernetes restarts) |
| `GET /actuator/health/readiness` | Public | Can the app take traffic? (e.g. for load balancers) |
| `GET /actuator/metrics`, `/actuator/metrics/{name}` | Any valid access token | Request counts and latency (`http.server.requests`), JVM and system metrics |


## Logging

- **Request ID:** every request gets an ID, returned in the `X-Request-Id` response header. A caller can send its own ID (letters, digits, `-` or `_`, up to 64 characters) to have it reused. Every log line written while handling the request starts with the ID, e.g. `[3f2a9c1e-...]`, so all the lines for one request can be found together.
- **One summary line per request:** e.g. `GET /api/v1/deals/peak-time -> 200 in 342 ms`.
- **Warnings:**
  - each feed retry, and the final failure after all attempts;
  - circuit breaker state changes, and requests failing fast while it's open (one line, no stack trace);
  - rejected access tokens, with the reason;
  - skipped or partial deal data.
- **Errors** (with a stack trace): real upstream failures, and unexpected server errors.
- **At startup:** one line with the feed URL, timeouts, retry and circuit-breaker settings.
- **More detail:** set `logging.level.com.eatclub.deals=DEBUG` to also log each upstream feed call, with its duration and the number of restaurants.
- **Never logged:** access tokens, the signing secret.
