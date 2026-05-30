package com.airasia.flight.event;

import com.airasia.flight.cache.CacheKeyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Backs event idempotency and ordering using Redis.
 *
 * <ul>
 *   <li><b>Idempotency:</b> each {@code eventId} is remembered for a window, so a
 *       redelivered/duplicate event is ignored.</li>
 *   <li><b>Ordering:</b> the timestamp of the last event applied to a
 *       (route,date) is stored; an event older than that (late / out-of-order
 *       delivery) is skipped so an older event can't clobber a newer state.</li>
 * </ul>
 */
@Component
public class ProcessedEventStore {

    private static final Duration PROCESSED_TTL = Duration.ofHours(6);
    private static final Duration LAST_APPLIED_TTL = Duration.ofHours(25);

    private final StringRedisTemplate redis;
    private final CacheKeyFactory keys;

    public ProcessedEventStore(StringRedisTemplate redis, CacheKeyFactory keys) {
        this.redis = redis;
        this.keys = keys;
    }

    public boolean isDuplicate(String eventId) {
        return Boolean.TRUE.equals(redis.hasKey(keys.processedEventKey(eventId)));
    }

    public void markProcessed(String eventId) {
        redis.opsForValue().set(keys.processedEventKey(eventId), "1", PROCESSED_TTL);
    }

    /** True if this event is at least as new as the last one applied to the date. */
    public boolean isNewerThanApplied(String origin, String destination, LocalDate date, long occurredAtEpochMillis) {
        String raw = redis.opsForValue().get(keys.lastAppliedKey(origin, destination, date));
        if (raw == null) {
            return true;
        }
        try {
            return occurredAtEpochMillis > Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public void recordApplied(String origin, String destination, LocalDate date, long occurredAtEpochMillis) {
        redis.opsForValue().set(keys.lastAppliedKey(origin, destination, date),
                Long.toString(occurredAtEpochMillis), LAST_APPLIED_TTL);
    }
}
