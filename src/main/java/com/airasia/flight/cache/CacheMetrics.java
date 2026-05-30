package com.airasia.flight.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom cache metrics surfaced via Micrometer/OpenTelemetry (scrapeable at
 * {@code /actuator/prometheus}): hit ratio, miss volume, and cache writes.
 */
@Component
public class CacheMetrics {

    private final Counter hits;
    private final Counter misses;
    private final Counter puts;

    public CacheMetrics(MeterRegistry registry) {
        this.hits = Counter.builder("lowfare.cache.hits")
                .description("Calendar cache hits").register(registry);
        this.misses = Counter.builder("lowfare.cache.misses")
                .description("Calendar cache misses").register(registry);
        this.puts = Counter.builder("lowfare.cache.puts")
                .description("Entries written to the calendar cache").register(registry);
    }

    public void recordHits(long count) {
        hits.increment(count);
    }

    public void recordMisses(long count) {
        misses.increment(count);
    }

    public void recordPut() {
        puts.increment();
    }
}
