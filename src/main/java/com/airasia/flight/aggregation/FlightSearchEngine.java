package com.airasia.flight.aggregation;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.model.SearchResult;
import com.airasia.flight.provider.FlightSearchQuery;
import com.airasia.flight.provider.FlightSearcherType;
import com.airasia.flight.provider.NavitaireSearcher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Scatter-gather engine across every {@link FlightSearcherType} (carrier),
 * mirroring the production {@code AbstractFlightSearchEngine}: submit one task
 * per carrier to the pool, collect {@code Future<SearchResult>[]}, then
 * {@code get(...)} each within a shared deadline and merge. Each call is guarded
 * by its own circuit breaker; a failing / slow / open carrier contributes
 * nothing rather than failing the request (graceful degradation). Finally the
 * cheapest fare per date wins.
 */
@Service
public class FlightSearchEngine {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchEngine.class);

    private final NavitaireSearcher searcher;
    private final ExecutorService executor;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final long timeoutMillis;

    public FlightSearchEngine(NavitaireSearcher searcher,
                              @Qualifier("providerExecutor") ExecutorService executor,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              MeterRegistry meterRegistry,
                              CalendarProperties properties) {
        this.searcher = searcher;
        this.executor = executor;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
        this.timeoutMillis = properties.providerTimeoutMillis();
    }

    /** Cheapest bookable fare per date across all carriers. */
    public Map<LocalDate, ProviderFare> lowestByDate(FlightSearchQuery query) {
        SearchResult consolidated = searchAllCarriers(query);
        return cheapestPerDate(consolidated.fares());
    }

    /** Fan out to every carrier in parallel and merge what comes back in time. */
    private SearchResult searchAllCarriers(FlightSearchQuery query) {
        FlightSearcherType[] carriers = FlightSearcherType.values();

        // Scatter: submit one task per carrier; all start running immediately.
        List<Future<SearchResult>> futures = new ArrayList<>(carriers.length);
        for (FlightSearcherType carrier : carriers) {
            futures.add(executor.submit(() -> searchCarrier(carrier, query)));
        }

        // Shared deadline keeps total wait <= timeoutMillis (P99 budget).
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        // Gather: merge each carrier's result, degrading on failure/timeout.
        SearchResult consolidated = new SearchResult();
        for (int i = 0; i < carriers.length; i++) {
            consolidated.mergeWith(await(carriers[i], futures.get(i), deadlineNanos));
        }
        return consolidated;
    }

    /** Runs on a pool thread: one carrier's search guarded by its circuit breaker. */
    private SearchResult searchCarrier(FlightSearcherType carrier, FlightSearchQuery query) {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(carrier.providerId());
        return SearchResult.of(breaker.executeSupplier(() -> searcher.search(carrier, query)));
    }

    /** Wait for one carrier within the remaining budget; never throws. */
    private SearchResult await(FlightSearcherType carrier, Future<SearchResult> future, long deadlineNanos) {
        try {
            long remainingMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
            SearchResult result = future.get(remainingMs, TimeUnit.MILLISECONDS);
            record(carrier, "success");
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            record(carrier, "timeout");
            log.warn("[{}] timed out after {} ms", carrier.providerId(), timeoutMillis);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CallNotPermittedException) {
                record(carrier, "circuit_open");
                log.debug("[{}] skipped — circuit open", carrier.providerId());
            } else {
                record(carrier, "failure");
                log.warn("[{}] failed: {}", carrier.providerId(),
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record(carrier, "failure");
            log.warn("[{}] interrupted", carrier.providerId());
        }
        return SearchResult.empty();
    }

    /** Reduce all fares to the cheapest one per date. */
    private Map<LocalDate, ProviderFare> cheapestPerDate(List<ProviderFare> fares) {
        Map<LocalDate, ProviderFare> cheapest = new HashMap<>();
        for (ProviderFare fare : fares) {
            cheapest.merge(fare.date(), fare,
                    (existing, candidate) ->
                            candidate.baseAmount().compareTo(existing.baseAmount()) < 0 ? candidate : existing);
        }
        return cheapest;
    }

    private void record(FlightSearcherType carrier, String outcome) {
        meterRegistry.counter("lowfare.provider.calls",
                "provider", carrier.providerId(), "outcome", outcome).increment();
    }
}
