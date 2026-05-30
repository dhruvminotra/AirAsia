package com.airasia.flight.cache;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Single source of truth for Redis key shapes.
 *
 * <h3>Why this cache key — {@code lowfare:{origin}:{destination}:{date}}</h3>
 * <ul>
 *   <li><b>Granularity = per date</b>, not per month. A sold-out event affects
 *       one date, so per-date keys let us invalidate/refresh exactly that date
 *       instead of rebuilding the whole month — and concurrent dates never
 *       contend on one large value.</li>
 *   <li><b>Currency is NOT in the key.</b> Values are stored in the base
 *       currency and converted at read time, so one cached entry serves MYR,
 *       USD, THB… This avoids an N-currencies write amplification and keeps the
 *       hit ratio high.</li>
 *   <li><b>Route is part of the key</b> (origin+destination) — the natural
 *       partition for sharding/locality.</li>
 *   <li><b>Stable colon-delimited prefix</b> ({@code lowfare:}) makes keys easy
 *       to scan, namespace, and reason about in Redis.</li>
 * </ul>
 */
@Component
public class CacheKeyFactory {

    private static final String FARE_PREFIX = "lowfare";
    private static final String EVENT_PROCESSED_PREFIX = "event:processed";
    private static final String LAST_APPLIED_PREFIX = "event:lastapplied";

    public String fareKey(String origin, String destination, LocalDate date) {
        return FARE_PREFIX + ":" + origin + ":" + destination + ":" + date;
    }

    /**
     * Idempotency marker for an already-processed event id.
     */
    public String processedEventKey(String eventId) {
        return EVENT_PROCESSED_PREFIX + ":" + eventId;
    }

    /**
     * Stores the timestamp of the last event applied to a (route,date) for ordering.
     */
    public String lastAppliedKey(String origin, String destination, LocalDate date) {
        return LAST_APPLIED_PREFIX + ":" + origin + ":" + destination + ":" + date;
    }
}
