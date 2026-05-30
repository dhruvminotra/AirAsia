package com.airasia.flight.provider;

import com.airasia.flight.model.ProviderFare;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Template-method base for every carrier searcher, mirroring {@code AbstractSearcher}
 * in our production stack. Subclasses implement {@link #providerId},
 * {@link #displayName}, and {@link #doSearch}; cross-cutting concerns — timing,
 * logging and filtering out unbookable (sold-out) fares — live here so every
 * provider behaves consistently. New carriers are added by dropping in a new
 * {@code @Component} that extends this class — the engine discovers them via
 * Spring list injection (Open/Closed).
 */
public abstract class AbstractFlightSearcher {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Stable id; also the Resilience4j circuit-breaker instance name. */
    public abstract String providerId();

    /** Human-friendly name surfaced in metrics/logs. */
    public abstract String displayName();

    public final List<ProviderFare> search(FlightSearchQuery query) {
        long start = System.nanoTime();
        try {
            List<ProviderFare> fares = doSearch(query);
            List<ProviderFare> bookable = filterExcluded(fares, query.excludedFareKeys());
            if (log.isDebugEnabled()) {
                log.debug("[{}] returned {} fares ({} bookable) in {} ms", providerId(),
                        fares.size(), bookable.size(), (System.nanoTime() - start) / 1_000_000);
            }
            return bookable;
        } catch (RuntimeException e) {
            // Surface as ProviderException so the aggregator/circuit breaker can
            // treat any supplier failure uniformly.
            throw new ProviderException(providerId(), e);
        }
    }

    /** Supplier-specific lookup. Implementations may simulate or call a real GDS. */
    protected abstract List<ProviderFare> doSearch(FlightSearchQuery query);

    private List<ProviderFare> filterExcluded(List<ProviderFare> fares, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return fares;
        }
        return fares.stream()
                .filter(f -> !excluded.contains(FareKeys.of(f)))
                .toList();
    }
}
