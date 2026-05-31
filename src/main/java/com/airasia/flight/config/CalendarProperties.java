package com.airasia.flight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Typed binding of the {@code calendar.*} block. Records with {@code @DefaultValue}
 * give us defaults when yaml is silent <i>and</i> keep the type immutable — so
 * configuration is read-only at runtime and the class stays free of getter/setter
 * boilerplate.
 *
 * <p>Test code uses {@link #defaults()} plus the small {@code with…} helpers to
 * vary one field at a time.
 */
@ConfigurationProperties(prefix = "calendar")
public record CalendarProperties(
        @DefaultValue("MYR") String baseCurrency,
        @DefaultValue("450") long providerTimeoutMillis,
        Map<String, BigDecimal> exchangeRates,
        @DefaultValue Cache cache,
        @DefaultValue PubSub pubsub,
        @DefaultValue Warming warming) {

    public CalendarProperties {
        if (exchangeRates == null) {
            exchangeRates = Map.of();
        }
    }

    /** Redis TTL knobs. */
    public record Cache(
            @DefaultValue("86400") long ttlSeconds,
            @DefaultValue("3600") long ttlJitterSeconds,
            @DefaultValue("300") long emptyTtlSeconds) {
    }

    /** Pub/Sub topic + subscription names. */
    public record PubSub(
            @DefaultValue("price-class-sold-out") String soldOutTopic,
            @DefaultValue("sold-out-sub") String soldOutSubscription) {
    }

    /** Startup cache warm-up routes. */
    public record Warming(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1") int monthsAhead,
            List<String> routes) {

        public Warming {
            if (routes == null) {
                routes = List.of();
            }
        }
    }

    // ------------------------------------------------------------------
    // Test helpers — concise construction with all fields at defaults,
    // plus single-field overrides. Production code never calls these.
    // ------------------------------------------------------------------

    public static CalendarProperties defaults() {
        return new CalendarProperties(
                "MYR", 450L, Map.of(),
                new Cache(86400, 3600, 300),
                new PubSub("price-class-sold-out", "sold-out-sub"),
                new Warming(true, 1, List.of()));
    }

    public CalendarProperties withBaseCurrency(String baseCurrency) {
        return new CalendarProperties(baseCurrency, providerTimeoutMillis, exchangeRates, cache, pubsub, warming);
    }

    public CalendarProperties withProviderTimeoutMillis(long providerTimeoutMillis) {
        return new CalendarProperties(baseCurrency, providerTimeoutMillis, exchangeRates, cache, pubsub, warming);
    }

    public CalendarProperties withExchangeRates(Map<String, BigDecimal> exchangeRates) {
        return new CalendarProperties(baseCurrency, providerTimeoutMillis, exchangeRates, cache, pubsub, warming);
    }
}
