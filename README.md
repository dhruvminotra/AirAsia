# AirAsia MOVE — Low Fare Calendar

Backend for the Low Fare Calendar: aggregates the cheapest flight per date across
3 providers, serves it in any currency, keeps prices fresh via async sold-out
events, and is built for 1000 TPS / P99 < 500ms.

> Full architecture, cache-key rationale, and design patterns are in
> **[DESIGN.md](DESIGN.md)**. AI collaboration notes are in **[AI_USAGE.md](AI_USAGE.md)**.

## Stack
- Java 17 + Spring Boot 3.2
- Redis (cache, idempotency/ordering)
- Google Cloud Pub/Sub (emulator) for sold-out events
- Resilience4j (circuit breakers), Micrometer/OpenTelemetry (cache metrics)

## Highlights
- **Multi-provider scatter-gather** on a bounded thread pool, cheapest fare per date.
- **Cache-aside Redis** with per-date keys, base-currency values, jittered TTL, negative caching.
- **Dynamic currency** — add a currency by editing config (Open/Closed), conversion at read time.
- **Async sold-out updates** with **idempotency + ordering** guards.
- **Thundering-herd protection** (in-process request coalescing / single-flight).
- **Circuit breakers + timeouts** for graceful degradation.
- **Custom cache metrics** at `/actuator/prometheus`.

## Run with Docker (recommended)

```bash
docker-compose up --build
```

This starts Redis, the Pub/Sub emulator, and the app. The app **auto-creates** the
`price-class-sold-out` topic and `sold-out-sub` subscription on startup — no manual
gcloud steps needed.

App: http://localhost:8080 · Metrics: http://localhost:8080/actuator/prometheus

## Run locally (without Docker)

Start Redis and the Pub/Sub emulator, export their hosts, then run the app:

```bash
# Redis
redis-server --notify-keyspace-events Ex

# Pub/Sub emulator
gcloud beta emulators pubsub start --project=test-project --host-port=localhost:8085
export PUBSUB_EMULATOR_HOST=localhost:8085

# App (Java 17 + Maven)
mvn spring-boot:run
```

## Try it

```bash
# Fetch the calendar (cheapest fare per day) in MYR
curl "http://localhost:8080/api/v1/flights/calendar?origin=KUL&destination=SIN&month=2026-06&currency=MYR"

# Same route in USD — served from the same cache, converted at read time
curl "http://localhost:8080/api/v1/flights/calendar?origin=KUL&destination=SIN&month=2026-06&currency=USD"

# Publish a sold-out event (async price correction)
curl -X POST http://localhost:8080/api/v1/flights/sold-out \
  -H "Content-Type: application/json" \
  -d '{"eventId":"e1","origin":"KUL","destination":"SIN","date":"2026-06-15","flightNumber":"AK100","priceClass":"PROMO","occurredAtEpochMillis":1717000000000}'
```

### Demo & load scripts
```bash
./scripts/demo.sh          # read -> currency -> sold-out -> re-read (price changes)
./scripts/load-test.sh     # REQUESTS=2000 CONCURRENCY=100 ./scripts/load-test.sh
```
A Postman collection is at `scripts/LowFareCalendar.postman_collection.json`.

## Testing

```bash
mvn test
```
Covers concurrency/aggregation, currency conversion, cache-miss + coalescing,
event idempotency/ordering, and the controller. `RedisIntegrationTest` runs against
a real Redis via Testcontainers (auto-skipped if Docker is unavailable).

## Configuration (key knobs in `application.yml`)
- `calendar.base-currency`, `calendar.exchange-rates.*` — add a currency here, no code change.
- `calendar.cache.*` — TTL, jitter, negative-cache TTL.
- `calendar.provider-timeout-millis` — per-provider hard timeout.
- `providers.*` — per-carrier IATA code + simulated latency/failure (to exercise the circuit breaker).
- `calendar.warming.*` — startup pre-population routes.
