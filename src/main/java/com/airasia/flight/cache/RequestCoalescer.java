package com.airasia.flight.cache;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-process single-flight: when many threads ask for the same key at once, only
 * the first executes the (expensive) loader; the rest await its result. This is
 * our thundering-herd protection — concurrent cache misses for the same route+month
 * trigger only one provider fan-out instead of one per request.
 */
@Component
public class RequestCoalescer {

    private final ConcurrentHashMap<String, CompletableFuture<?>> inFlight = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T compute(String key, Supplier<T> loader) {
        CompletableFuture<T> mine = new CompletableFuture<>();
        CompletableFuture<T> existing = (CompletableFuture<T>) inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            // Someone else is already loading this key — wait for their result.
            return existing.join();
        }
        try {
            T value = loader.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }
}
