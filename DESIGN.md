# Design — Low Fare Calendar System

This document covers the High-Level Design (HLD) and Low-Level Design (LLD) for
the AirAsia MOVE Low Fare Calendar backend.

---

## 1. High-Level Design

### 1.1 Components

```
                                  ┌──────────────────────────────────────────┐
                                  │            Low Fare Calendar App           │
                                  │                (Spring Boot)               │
   GET /calendar  ──────────────▶ │  ┌─────────────┐    ┌──────────────────┐  │
   (user, any currency)           │  │ Controller  │───▶│  CalendarService  │  │
                                  │  └─────────────┘    │  (orchestration)  │  │
                                  │                     └───────┬──────────┘  │
                                  │      cache-aside read       │             │
                                  │     ┌───────────────────────┘             │
                                  │     ▼                                      │
                                  │  ┌────────────┐  miss  ┌───────────────┐  │      ┌─────────────┐
                                  │  │ LowFareCache│──────▶│ RequestCoalescer│─┼────▶│   Redis     │
                                  │  └────────────┘        │ (single-flight) │ │      │ (cache +    │
                                  │        ▲               └───────┬────────┘ │      │  idempotency)│
                                  │        │ write                 ▼          │      └─────────────┘
                                  │        │             ┌──────────────────┐ │
                                  │        │             │ FlightSearchEngine│ │
                                  │        │             │ (scatter-gather)  │ │
                                  │        │             └───────┬──────────┘ │
                                  │        │      ┌──────────────┼───────────┐│
                                  │        │      ▼              ▼           ▼│   ┌──────────────────┐
                                  │        │  SabreSearcher  AmadeusSearcher  GalileoSearcher │ (3 GDS suppliers,│
                                  │        │  (CircuitBreaker each, bounded pool)             │  parallel calls) │
                                  │        │                                  │   └──────────────────┘
                                  │  ┌─────┴───────────────┐                  │
                                  │  │ SoldOutEventSubscriber│◀── price-class-sold-out ──┐
                                  │  └─────────────────────┘                  │          │
                                  └──────────────────────────────────────────┘          │
                                                                              ┌──────────┴─────────┐
   POST /sold-out (test/ops) ──────────▶  SoldOutEventPublisher  ───────────▶│ Google Cloud Pub/Sub│
   (prod: booking/listing services)                                          │     (emulator)      │
                                                                             └────────────────────┘
```

| Component | Responsibility |
|-----------|----------------|
| `FlightCalendarController` | Thin REST entry point (`flight.calendar`); validates the request and delegates to `CalendarService` / `SoldOutEventPublisher`. |
| `CalendarService` | Orchestrates cache-aside read → coalesced miss load → aggregation → currency conversion → `FareCalendarResponse`. |
| `AbstractFlightSearcher` | Template-method base mirroring the prod `AbstractSearcher`. Each subclass declares `providerId()` + `displayName()` + `doSearch(query)`. |
| `SabreSearcher` / `AmadeusSearcher` / `GalileoSearcher` | Three independent `@Component` subclasses — one per GDS supplier. Each owns its own `doSearch(query)` (in production: its own SDK / SOAP / REST adapter) and applies its own pricing factor → **same flight returned by all three at different prices** (the assignment's exact scenario). |
| `FlightSearchEngine` | Parallel scatter-gather across carriers (submit `Callable` → `Future<SearchResult>[]` → get+merge), circuit breaker per provider, picks lowest. Mirrors prod `AbstractFlightSearchEngine`. |
| `LowFareCache` | Cache-aside Redis repository (base-currency values, bulk `MGET`). |
| `RequestCoalescer` | Thundering-herd protection (in-process single-flight per route+month). |
| `CurrencyConversionService` | Converts base → requested currency at read time. |
| `SoldOutEventSubscriber` / `Publisher` | Async price-correction via Pub/Sub. |
| `ProcessedEventStore` | Event idempotency + ordering (Redis). |
| `CacheMetrics` | Custom OTel/Micrometer cache metrics (hits/misses/puts/stale). |
| `CacheWarmingService` | Startup pre-population of popular routes. |

### 1.2 Read flow (calendar view)

1. `GET /api/v1/flights/calendar?origin&destination&month&currency`.
2. Validate currency; expand the month into its list of dates.
3. **Bulk** cache read (`MGET`) for all dates in one round trip.
4. For the dates that missed:
   - Wrapped in `RequestCoalescer` (one in-flight build per route+month per JVM), so concurrent misses for the same key trigger a single provider fan-out.
   - `FlightSearchEngine` submits one `Callable` per carrier to the pool, collects `Future<SearchResult>[]`, then `get(...)`s each within a **shared deadline** and merges them — each call guarded by a **circuit breaker**; slow/failed/open providers contribute nothing. The cheapest fare per date wins.
   - Results (or negative markers for empty dates) are written to Redis with a **jittered TTL**.
5. Convert each base-currency value to the requested currency.
6. Return `FareCalendarResponse`.

### 1.3 Write flow (async sold-out / price correction)

1. Booking/listing service (or the test endpoint) publishes a `price-class-sold-out` event to Pub/Sub.
2. `SoldOutEventSubscriber` consumes it and:
   - **Idempotency**: skip if `eventId` already processed.
   - **Ordering**: skip if older than the last event applied to that (route,date).
   - Re-aggregates that single date **excluding the sold-out fare**, picking the next lowest.
   - Overwrites the cache entry (or writes a negative marker if nothing is left).
   - Records `lastApplied` timestamp + `processed` marker, then **acks**. On failure it **nacks** (redelivery); processing is idempotent.

### 1.4 Cache pre-population (warming)

- On `ApplicationReadyEvent`, `CacheWarmingService` asynchronously warms a configured set of popular routes for the current month + N months ahead, reusing the normal read path. This means the first real users hit a warm cache.
- A scheduled refresh job for the top-N routes can be added the same way (`@Scheduled`) — the read path is reused, so warming and live traffic are identical.

---

## 2. Low-Level Design

### 2.1 API contracts

**Read**

```
GET /api/v1/flights/calendar?origin=KUL&destination=SIN&month=2026-06&currency=MYR

200 OK
{
  "origin": "KUL",
  "destination": "SIN",
  "month": "2026-06",
  "currency": "MYR",
  "days": [
    { "date": "2026-06-01", "price": 149.50, "available": true },
    { "date": "2026-06-02", "available": false }
  ]
}
```
- `price` is omitted when `available` is false (no bookable inventory).
- `400` for an unsupported currency or a malformed month.

**Write (test/ops hook; prod source is the booking/listing services)**

```
POST /api/v1/flights/sold-out
{
  "eventId": "uuid-1",
  "origin": "KUL",
  "destination": "SIN",
  "date": "2026-06-15",
  "flightNumber": "AK100",
  "priceClass": "PROMO",
  "occurredAtEpochMillis": 1717000000000
}

202 Accepted   (empty body — the event is processed asynchronously)
```

### 2.2 Cache key design (and the rationale — *mandatory*)

**Key:** `lowfare:{origin}:{destination}:{date}` → JSON `CachedLowFare` (base currency).

Decision process:

1. **Granularity: per-date, not per-month.** A sold-out event invalidates exactly
   one date. A per-month blob would force a full rebuild for a single date change
   and create one hot, large value many requests contend on. Per-date keys make
   invalidation surgical and spread load across keys. The read still does a single
   `MGET` of ~30 keys, so we don't pay a round-trip-per-day penalty.
2. **Currency is NOT in the key.** Values are stored in a single **base currency**
   and converted at read time. Putting currency in the key would multiply writes
   and slash the hit ratio (the same date cached N times for N currencies) and
   would mean a sold-out event has to invalidate N entries. One currency-agnostic
   entry serves everyone; conversion is a cheap multiply.
3. **Route in the key** (origin+destination) is the natural sharding/locality unit.
4. **Stable `lowfare:` prefix** namespaces keys for scanning, metrics, and reasoning
   about the keyspace in Redis.

Related keys: `event:processed:{eventId}` (idempotency),
`event:lastapplied:{o}:{d}:{date}` (ordering).

**Value** (`CachedLowFare`): `{date, baseAmount, provider, flightNumber, priceClass, empty}`.
Provenance (provider/flight/class) makes sold-out matching precise and powers
observability; `empty=true` is the negative-cache marker.

### 2.3 Key design patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Template Method** | `AbstractFlightSearcher` → `SabreSearcher`/`AmadeusSearcher`/`GalileoSearcher` | One template enforces cross-cutting concerns (timing, sold-out filter); each GDS subclass plugs in only its identity + `doSearch`. Mirrors the prod `AbstractSearcher`. |
| **Strategy + Open/Closed** | `ExchangeRateProvider` / currencies as config | New currency = a config row, no code. Live FX feed = a new `ExchangeRateProvider` impl, no caller change. |
| **Scatter-Gather** | `FlightSearchEngine` | Submit a `Callable` per provider → `Future<SearchResult>[]` → `get(...)` within a shared deadline → merge → reduce-to-min. Mirrors prod `AbstractFlightSearchEngine`. |
| **Cache-Aside** | `LowFareCache` + `CalendarService` | Read-through with explicit population and negative caching. |
| **Single-Flight / Coalescing** | `RequestCoalescer` | Thundering-herd protection (one in-flight build per route+month). |
| **Circuit Breaker** | Resilience4j per provider | Fault isolation + graceful degradation. |
| **Open/Closed (plug-in discovery)** | `List<AbstractFlightSearcher>` Spring injection | Add a GDS = drop in a new `@Component` extending `AbstractFlightSearcher` + a yaml block. Engine, breaker, metrics adapt automatically. |

Class sketch:

```
FlightCalendarController ──▶ CalendarService ──has──▶ LowFareCache, FlightSearchEngine,
                                                      CurrencyConversionService, RequestCoalescer
                         ──▶ SoldOutEventPublisher (POST /sold-out)

AbstractFlightSearcher (template — search() → doSearch(); shared simulateLatency/Failure)
   ▲                  ▲                    ▲
SabreSearcher    AmadeusSearcher    GalileoSearcher
("sabre",        ("amadeus",        ("galileo",
 markup 1.03)     markup 1.07)       markup 1.05)
   │                  │                    │
   └─ each owns its own doSearch() → same flight (AK100/101/102), different prices ─┘

FlightSearchEngine ──has──▶ List<AbstractFlightSearcher>, ExecutorService(bounded pool), CircuitBreakerRegistry
                   ──per provider──▶ Callable<SearchResult>  (SearchResult wraps List<ProviderFare>)
```

---

## 3. Meeting 1000 TPS & P99 < 500ms

- **Cache-first**: steady state is an `MGET` + in-memory conversion → sub-millisecond
  server work; Redis comfortably serves well beyond 1000 TPS. The provider path is
  the exception, not the rule.
- **Thundering-herd control**: on expiry/invalidation, in-process coalescing ensures
  ~1 provider rebuild per route+month per instance instead of 1000 concurrent fan-outs.
- **Bounded provider latency**: each provider call has a hard timeout (450ms default,
  inside the 500ms budget); slow providers are dropped, not waited on.
- **Bounded thread pool** (with caller-runs back-pressure) fans out the scatter-gather
  cheaply; because reads are cache-first and provider rebuilds are coalesced, only a
  few fan-outs run concurrently, so a capped pool is sufficient.
- **TTL jitter** prevents synchronised mass expiry (a self-inflicted stampede).
- **Negative caching** stops empty dates from repeatedly hammering providers.

Network failures to providers are handled by per-provider circuit breakers + timeout
+ "missing provider contributes nothing" degradation, so one bad provider never
fails the whole request; stale cache is preferred over an error.

---

## 4. Bonus features

- **OpenTelemetry / Micrometer**: `lowfare.cache.{hits,misses,puts,stale_served}`
  and `lowfare.provider.calls{provider,outcome}` at `/actuator/prometheus`.
- **Thundering Herd**: `RequestCoalescer` (in-process single-flight) collapses
  concurrent misses for the same route+month into one provider fan-out. For a
  multi-instance deployment this extends naturally to a Redis `SET NX PX` lock.
- **Provider Fault Tolerance**: mock providers inject configurable latency/failure;
  Resilience4j circuit breakers + timeouts degrade gracefully and fall back to cache.
- **Event Idempotency & Ordering**: `eventId` dedupe + `lastApplied` timestamp guard.
  A late "price drop" generated *before* a later "sold out" is rejected by the
  ordering guard (its timestamp is older than the applied one), so an older event
  can't overwrite newer state. Because every event triggers a fresh re-aggregation
  rather than blindly applying a delta, the cache always converges to current
  provider truth regardless of arrival order.

---

## 5. Trade-offs & notes

- Providers are **mocked** on purpose — the brief asks for design quality, not three
  real SDK integrations. Each GDS searcher owns its own `doSearch(query)`, so
  swapping the mock for the real Sabre, Amadeus, and Galileo adapters is a per-class
  change (their schemas differ in production) with no engine impact. The abstraction
  mirrors our prod `AbstractSearcher` / `SabreSearcher` / `AmadeusV2Searcher` /
  `GalileoSearcher` stack.
- Conversion-at-read trades a tiny per-response CPU cost for a far higher hit ratio
  and simpler invalidation — the right trade for a read-heavy calendar.
- Thundering-herd protection is **in-process** (single-flight). For a single service
  instance this fully prevents a stampede; scaling horizontally would add a Redis
  `SET NX` build lock — deliberately left out to keep the design lean.
