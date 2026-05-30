package com.airasia.flight.aggregation;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.model.SearchResult;
import com.airasia.flight.provider.AbstractFlightSearcher;
import com.airasia.flight.provider.FlightSearchQuery;
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
 * Scatter-gather engine across all carrier providers (discovered via Spring list
 * injection), mirroring the production {@code AbstractFlightSearchEngine}: submit
 * one {@link SearchResult}-returning task per provider, collect the
 * {@link Future}s, then {@code get(...)} each within a shared deadline and merge
 * the results. Each call is guarded by its own circuit breaker; a failing / slow /
 * open provider contributes nothing rather than failing the request (graceful
 * degradation). Finally the cheapest fare per date wins.
 */
@Service
public class FlightSearchEngine {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchEngine.class);

    private final List<AbstractFlightSearcher> providers;
    private final ExecutorService executor;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final long timeoutMillis;

    public FlightSearchEngine(List<AbstractFlightSearcher> providers, @Qualifier("providerExecutor") ExecutorService executor, CircuitBreakerRegistry circuitBreakerRegistry, MeterRegistry meterRegistry, CalendarProperties properties) {
        this.providers = providers;
        this.executor = executor;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
        this.timeoutMillis = properties.getProviderTimeoutMillis();
    }

    /**
     * Cheapest bookable fare per date across all providers.
     */
    public Map<LocalDate, ProviderFare> lowestByDate(FlightSearchQuery query) {
        SearchResult consolidated = searchAllProviders(query);
        return cheapestPerDate(consolidated.fares());
    }

    /**
     * Fan out to every provider in parallel and merge what comes back in time.
     */
    private SearchResult searchAllProviders(FlightSearchQuery query) {
        // Scatter: submit one task per provider; all start running immediately.
        List<Future<SearchResult>> futures = new ArrayList<>(providers.size());
        for (AbstractFlightSearcher provider : providers) {
            futures.add(executor.submit(() -> searchProvider(provider, query)));
        }

        // A single deadline shared by all gets, so three hung providers can't sum
        // their timeouts and blow the latency budget.
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        // Gather: merge each provider's result, degrading on failure/timeout.
        SearchResult consolidated = new SearchResult();
        for (int i = 0; i < providers.size(); i++) {
            consolidated.mergeWith(await(providers.get(i), futures.get(i), deadlineNanos));
        }
        return consolidated;
    }

    /**
     * Runs on a pool thread: invoke one carrier's searcher, guarded by that
     * carrier's circuit breaker. Returns a {@link SearchResult} so the caller
     * can merge it with the others.
     */
    private SearchResult searchProvider(AbstractFlightSearcher provider, FlightSearchQuery query) {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(provider.providerId());
        return SearchResult.of(breaker.executeSupplier(() -> provider.search(query)));
    }

    /**
     * Wait for one provider within the remaining budget; never throws.
     */
    private SearchResult await(AbstractFlightSearcher provider, Future<SearchResult> future, long deadlineNanos) {
        try {
            long remainingMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
            SearchResult result = future.get(remainingMs, TimeUnit.MILLISECONDS);
            record(provider, "success");
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            record(provider, "timeout");
            log.warn("[{}] timed out after {} ms", provider.providerId(), timeoutMillis);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CallNotPermittedException) {
                record(provider, "circuit_open");
                log.debug("[{}] skipped — circuit open", provider.providerId());
            } else {
                record(provider, "failure");
                log.warn("[{}] failed: {}", provider.providerId(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record(provider, "failure");
            log.warn("[{}] interrupted", provider.providerId());
        }
        return SearchResult.empty();
    }

    /**
     * Reduce all fares to the cheapest one per date.
     */
    private Map<LocalDate, ProviderFare> cheapestPerDate(List<ProviderFare> fares) {
        Map<LocalDate, ProviderFare> cheapest = new HashMap<>();
        for (ProviderFare fare : fares) {
            cheapest.merge(fare.date(), fare, (existing, candidate) -> candidate.baseAmount().compareTo(existing.baseAmount()) < 0 ? candidate : existing);
        }
        return cheapest;
    }

    private void record(AbstractFlightSearcher provider, String outcome) {
        meterRegistry.counter("lowfare.provider.calls", "provider", provider.providerId(), "outcome", outcome).increment();
    }
}
