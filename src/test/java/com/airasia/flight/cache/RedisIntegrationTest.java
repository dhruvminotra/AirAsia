package com.airasia.flight.cache;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.event.ProcessedEventStore;
import com.airasia.flight.model.CachedLowFare;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the cache layer, distributed lock and event store against a real
 * Redis (Testcontainers). Skips automatically when Docker isn't available.
 */
class RedisIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 20);

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private static LowFareCache cache;
    private static ProcessedEventStore processedEvents;

    @BeforeAll
    static void startRedis() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();

        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        CacheKeyFactory keys = new CacheKeyFactory();
        CalendarProperties properties = CalendarProperties.defaults();
        cache = new LowFareCache(redisTemplate, objectMapper, keys,
                new CacheMetrics(new SimpleMeterRegistry()), properties);
        processedEvents = new ProcessedEventStore(redisTemplate, keys);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @Test
    void cacheRoundTripsFareThroughRedis() {
        CachedLowFare fare = new CachedLowFare(DATE, new BigDecimal("123.45"),
                "sabre", "AK100", "PROMO", false);
        cache.put("KUL", "SIN", fare);

        Map<LocalDate, CachedLowFare> loaded = cache.getAll("KUL", "SIN", List.of(DATE));
        assertThat(loaded).containsKey(DATE);
        assertThat(loaded.get(DATE).baseAmount()).isEqualByComparingTo("123.45");
        assertThat(loaded.get(DATE).provider()).isEqualTo("sabre");
    }

    @Test
    void cacheStoresNegativeMarker() {
        LocalDate emptyDate = DATE.plusDays(1);
        cache.put("KUL", "SIN", CachedLowFare.empty(emptyDate));

        Map<LocalDate, CachedLowFare> loaded = cache.getAll("KUL", "SIN", List.of(emptyDate));
        assertThat(loaded.get(emptyDate).empty()).isTrue();
    }

    @Test
    void eventStoreTracksIdempotencyAndOrdering() {
        assertThat(processedEvents.isDuplicate("evt-1")).isFalse();
        processedEvents.markProcessed("evt-1");
        assertThat(processedEvents.isDuplicate("evt-1")).isTrue();

        assertThat(processedEvents.isNewerThanApplied("KUL", "SIN", DATE, 100L)).isTrue();
        processedEvents.recordApplied("KUL", "SIN", DATE, 100L);
        assertThat(processedEvents.isNewerThanApplied("KUL", "SIN", DATE, 50L)).isFalse();
        assertThat(processedEvents.isNewerThanApplied("KUL", "SIN", DATE, 200L)).isTrue();
    }
}
