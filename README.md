# AirAsia MOVE — Low Fare Calendar

Backend for the Low Fare Calendar. For a route and month, it aggregates the
cheapest fare per date across **three GDS providers (Sabre, Amadeus, Galileo)**,
caches results in Redis in a base currency, serves the calendar in any
requested currency, and keeps prices fresh via async sold-out events published
on Pub/Sub. Designed for **1000 TPS / P99 < 500ms**.

> Architecture, cache-key rationale, and design patterns are in
> **[DESIGN.md](DESIGN.md)**. AI collaboration notes are in **[AI_USAGE.md](AI_USAGE.md)**.

---

## ▶️ Run with Docker (one command)

```bash
docker compose up --build
```

That brings up three containers: **Redis**, the **Google Cloud Pub/Sub emulator**,
and the **Spring Boot app**. The app auto-creates the topic and subscription on
startup — no manual gcloud steps required.

When you see `Started AirAsiaCalendarApplication in N seconds` on the logs,
the app is ready on:

| Endpoint | URL |
|---|---|
| REST API | http://localhost:8080/api/v1/flights/* |
| Health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |

To stop: `Ctrl+C`, then `docker compose down`.

---

## 🧪 Try it (after `docker compose up`)

```bash
# 1. Calendar in USD — cheapest fare per day for the whole month
curl -s "http://localhost:8080/api/v1/flights/calendar?origin=KUL&destination=SIN&month=2026-12&currency=USD" | jq

# 2. Same route in MYR — served from the same cache, converted at read time
curl -s "http://localhost:8080/api/v1/flights/calendar?origin=KUL&destination=SIN&month=2026-12&currency=MYR" | jq '.days[0:3]'

# 3. Unsupported currency → 400 Bad Request
curl -i "http://localhost:8080/api/v1/flights/calendar?origin=KUL&destination=SIN&month=2026-12&currency=XYZ"

# 4. Publish a sold-out event (async price correction)
curl -X POST http://localhost:8080/api/v1/flights/sold-out \
  -H "Content-Type: application/json" \
  -d '{
    "eventId":"demo-1",
    "origin":"KUL",
    "destination":"SIN",
    "date":"2026-12-15",
    "flightNumber":"AK100",
    "priceClass":"PROMO",
    "occurredAtEpochMillis": 1735689600000
  }'

# 5. Watch per-provider + cache metrics
curl -s localhost:8080/actuator/prometheus | grep "lowfare_"
```

End-to-end demo script (read → currency → sold-out → re-read → metrics):
```bash
./scripts/demo.sh
```

A Postman collection lives at `scripts/LowFareCalendar.postman_collection.json`.

---

## ▶️ Run without Docker (alternative)

You need Redis, the Pub/Sub emulator, and JDK 17 on PATH.

```bash
# 1. Redis
redis-server --daemonize yes --port 6379

# 2. Pub/Sub emulator (run in its own terminal)
gcloud beta emulators pubsub start --project=test-project --host-port=localhost:8085

# 3. The app
mvn -q package -DskipTests
java -jar target/low-fare-calendar-1.0.0.jar
```

The same `application.yml` works locally and in Docker — env-var placeholders
(`${SPRING_REDIS_HOST:localhost}`, etc.) fall back to localhost when not set.

---

## 🧰 Stack

- **Java 17 + Spring Boot 3.2**
- **Redis** — cache, idempotency, ordering watermarks
- **Google Cloud Pub/Sub (emulator)** — sold-out event bus
- **Resilience4j** — per-provider circuit breakers
- **Micrometer / OpenTelemetry** — custom cache + provider metrics

## ⭐ Highlights

- **3-provider scatter-gather** on a bounded thread pool with a shared deadline → cheapest fare per date.
- **Cache-aside Redis**, per-date keys, base-currency values, jittered TTL, negative caching.
- **Dynamic currency** — adding a currency is a yaml row (Open/Closed); conversion at read time keeps one cached value serving every currency.
- **Async sold-out updates** with **idempotency + ordering** guards (Redis-backed).
- **Thundering-herd protection** — in-process single-flight (`RequestCoalescer`).
- **Circuit breaker per provider** + simulated latency/failures → live fault-tolerance demo.
- **Observability** — `/actuator/prometheus` exposes cache hits/misses/puts and per-provider call counts tagged by outcome.

---

## 🧪 Testing

```bash
mvn test
```
24 tests covering scatter-gather concurrency, currency conversion, cache-miss
coalescing, idempotency + ordering, and the controller. `RedisIntegrationTest`
runs against a real Redis via Testcontainers (auto-skipped if Docker isn't
available).

---

## ⚙️ Configuration (`application.yml`)

| Key | Purpose |
|---|---|
| `calendar.base-currency`, `calendar.exchange-rates.*` | Add a currency here — no code change. |
| `calendar.cache.*` | TTL, jitter, negative-cache TTL. |
| `calendar.provider-timeout-millis` | Per-provider hard timeout (default 450 ms). |
| `calendar.warming.*` | Routes to pre-warm at startup. |
| `providers.settings.{sabre,amadeus,galileo}` | Simulated latency/failure rate per GDS (exercises the circuit breaker). |
| `resilience4j.circuitbreaker.*` | Breaker tuning per provider. |
