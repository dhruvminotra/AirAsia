package com.airasia.flight.cache;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.CachedLowFare;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cache-aside repository for low fares in Redis. Stores {@link CachedLowFare} as
 * JSON in the base currency. Reads are bulk ({@code MGET}) so an entire month is
 * fetched in a single round trip.
 */
@Repository
public class LowFareCache {

    private static final Logger log = LoggerFactory.getLogger(LowFareCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CacheKeyFactory keys;
    private final CacheMetrics metrics;
    private final CalendarProperties.Cache cacheProps;

    public LowFareCache(StringRedisTemplate redis, ObjectMapper objectMapper, CacheKeyFactory keys,
                        CacheMetrics metrics, CalendarProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.keys = keys;
        this.metrics = metrics;
        this.cacheProps = properties.cache();
    }

    /**
     * Bulk read for a month. Returns only the dates present in the cache; absent
     * dates are reported as misses (both metric and the returned map's absence).
     */
    public Map<LocalDate, CachedLowFare> getAll(String origin, String destination, List<LocalDate> dates) {
        List<String> keyList = dates.stream()
                .map(d -> keys.fareKey(origin, destination, d))
                .toList();
        List<String> values = redis.opsForValue().multiGet(keyList);

        Map<LocalDate, CachedLowFare> result = new LinkedHashMap<>();
        long hits = 0;
        long misses = 0;
        for (int i = 0; i < dates.size(); i++) {
            String raw = values == null ? null : values.get(i);
            CachedLowFare fare = deserialize(raw);
            if (fare != null) {
                result.put(dates.get(i), fare);
                hits++;
            } else {
                misses++;
            }
        }
        metrics.recordHits(hits);
        metrics.recordMisses(misses);
        return result;
    }

    public void put(String origin, String destination, CachedLowFare fare) {
        String key = keys.fareKey(origin, destination, fare.date());
        Duration ttl = fare.empty() ? Duration.ofSeconds(cacheProps.emptyTtlSeconds()) : jitteredTtl();
        write(key, fare, ttl);
        metrics.recordPut();
    }

    private void write(String key, CachedLowFare fare, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(fare), ttl);
        } catch (Exception e) {
            // Cache writes must never fail the request path.
            log.warn("Failed to write cache key {}: {}", key, e.getMessage());
        }
    }

    private CachedLowFare deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, CachedLowFare.class);
        } catch (Exception e) {
            log.warn("Corrupt cache value, treating as miss: {}", e.getMessage());
            return null;
        }
    }

    /** Base TTL plus a per-key random jitter to avoid synchronised mass expiry. */
    private Duration jitteredTtl() {
        long jitter = cacheProps.ttlJitterSeconds() <= 0
                ? 0 : ThreadLocalRandom.current().nextLong(cacheProps.ttlJitterSeconds() + 1);
        return Duration.ofSeconds(cacheProps.ttlSeconds() + jitter);
    }
}
