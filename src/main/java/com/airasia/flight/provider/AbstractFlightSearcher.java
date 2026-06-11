package com.airasia.flight.provider;

import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.ProviderProperties.ProviderSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractFlightSearcher {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public abstract String providerId();

    public abstract String displayName();

    public final List<ProviderFare> search(FlightSearchQuery query) {
        long start = System.nanoTime();
        List<ProviderFare> fares = doSearch(query);
        List<ProviderFare> bookable = filterExcluded(fares, query.excludedFareKeys());
        if (log.isDebugEnabled()) {
            log.debug("[{}] returned {} fares ({} bookable) in {} ms",
                    providerId(), fares.size(), bookable.size(),
                    (System.nanoTime() - start) / 1_000_000);
        }
        return bookable;
    }

    protected abstract List<ProviderFare> doSearch(FlightSearchQuery query);

    protected void simulateLatency(ProviderSettings settings) {
        long min = settings.getMinLatencyMillis();
        long max = Math.max(min, settings.getMaxLatencyMillis());
        long delay = (max == min) ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while contacting " + providerId(), e);
        }
    }

    protected void simulateFailure(ProviderSettings settings) {
        if (settings.getFailureRate() > 0
                && ThreadLocalRandom.current().nextDouble() < settings.getFailureRate()) {
            throw new IllegalStateException("Simulated upstream failure from " + providerId());
        }
    }

    private List<ProviderFare> filterExcluded(List<ProviderFare> fares, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return fares;
        }
        return fares.stream()
                .filter(f -> !excluded.contains(FareKeys.of(f)))
                .toList();
    }
}
