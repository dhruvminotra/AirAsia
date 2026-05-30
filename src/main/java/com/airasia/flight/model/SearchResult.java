package com.airasia.flight.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable holder for the fares one provider returned, and the unit the engine
 * consolidates across providers. Mirrors the production {@code SearchResult} —
 * each provider call yields a {@code SearchResult}, and the engine
 * {@link #mergeWith(SearchResult) merges} them into one before picking the lowest.
 */
public class SearchResult {

    private final List<ProviderFare> fares = new ArrayList<>();

    public static SearchResult of(List<ProviderFare> fares) {
        SearchResult result = new SearchResult();
        if (fares != null) {
            result.fares.addAll(fares);
        }
        return result;
    }

    public static SearchResult empty() {
        return new SearchResult();
    }

    /** Absorb another provider's fares into this consolidated result. */
    public void mergeWith(SearchResult other) {
        if (other != null) {
            this.fares.addAll(other.fares);
        }
    }

    public List<ProviderFare> fares() {
        return fares;
    }
}
