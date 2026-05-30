# AI Collaboration Reflection

**Tools used:** Claude (Anthropic) via Claude Code as the primary pair-programming
assistant, plus GitHub Copilot for in-editor autocompletion.

## How AI was used

- **Scaffolding & boilerplate:** generating the package layout, Spring config
  classes, `@ConfigurationProperties` getters/setters, the Pub/Sub bootstrap, and
  the first drafts of unit tests — the repetitive parts where AI is fastest.
- **Sounding board for design:** discussing the cache-key shape, where currency
  conversion should live, and how to structure thundering-herd protection.
- **Test generation:** drafting Mockito/AssertJ tests for the aggregator,
  service, and event subscriber, which I then tightened (matchers, captors).

## Representative prompts

- *"Design a low-fare calendar: aggregate 3 providers, pick the lowest, cache in
  Redis per date, update asynchronously on sold-out events. What should the cache
  key be?"*
- *"Show a scatter-gather across providers with CompletableFuture on a bounded
  thread pool (Java 17), each call wrapped in a Resilience4j circuit breaker and a
  timeout, where a failing provider degrades instead of failing the request."*
- *"How do I prevent 1000 concurrent requests from all rebuilding the same cache
  key — both within one JVM and across instances?"*

## A scenario where I had to correct the AI

**Currency strategy.** The first AI suggestion was the textbook Strategy pattern:
a `CurrencyConverter` interface with one implementation per currency
(`UsdConverter`, `ThbConverter`, …) chosen by a factory. It works, but it still
requires a **new class per currency** — which only partly satisfies the
Open/Closed requirement and adds ceremony.

I pushed back and redesigned it as a **data-driven** `ExchangeRateProvider` backed
by a config map (`calendar.exchange-rates`), with conversion applied at read time
on a single base-currency cache value. Now adding a currency is a **one-line config
change** with zero code, and the interface still leaves room to swap in a live FX
feed later. This also influenced the cache-key decision: because conversion is at
read time, currency is deliberately **kept out of the key**, which keeps the hit
ratio high and makes sold-out invalidation touch a single entry.

**Cache stampede.** AI's initial cache code used a fixed TTL. I added per-key TTL
**jitter** to avoid synchronised mass expiry, and an in-process `RequestCoalescer`
(single-flight) so concurrent misses for the same route+month trigger only one
provider fan-out. (A cross-instance Redis build-lock is described in DESIGN.md as the
scale-out extension; left out of code to keep it lean.)

## Impact on the final code
AI clearly accelerated scaffolding and test drafting. The architecturally important
decisions — currency as data not classes, currency excluded from the cache key,
single-flight thundering-herd protection, and re-aggregating on every event rather
than applying deltas (for order-independence) — came from reviewing and correcting
the AI's suggestions, not accepting them verbatim.
