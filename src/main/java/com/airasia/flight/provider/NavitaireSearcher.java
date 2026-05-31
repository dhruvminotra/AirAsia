package com.airasia.flight.provider;

import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.ProviderProperties.ProviderSettings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single Navitaire-style searcher used for every AirAsia Group carrier — all of
 * them sit on the same Navitaire New Skies, so one class with a {@link
 * FlightSearcherType} parameter replaces three identical subclasses.
 *
 * <p>This is a <b>mock</b>: it synthesises deterministic prices from
 * (route, date, flight, providerId) so the calendar, currency, caching and event
 * flows can be demonstrated end-to-end without a real GDS. Latency and failure
 * are <b>simulated</b> from per-carrier config so the circuit breaker and
 * timeout path can be exercised.
 */
@Component
public class NavitaireSearcher {

    private static final int FLIGHTS_PER_DAY = 3;
    private static final String[] PRICE_CLASSES = {"PROMO", "SAVER", "FLEX"};
    private static final double[] CLASS_MULTIPLIER = {1.00, 1.35, 1.80};

    private final ProviderProperties properties;

    public NavitaireSearcher(ProviderProperties properties) {
        this.properties = properties;
    }

    /**
     * Look up fares for one carrier. Returns only bookable fares — anything in
     * {@link FlightSearchQuery#excludedFareKeys()} (sold-out) is filtered out.
     */
    public List<ProviderFare> search(FlightSearcherType type, FlightSearchQuery query) {
        ProviderSettings settings = properties.settingsFor(type);
        simulateLatency(settings);
        simulateFailure(type, settings);

        List<ProviderFare> fares = generate(type, query);
        return filterExcluded(fares, query.excludedFareKeys());
    }

    private List<ProviderFare> generate(FlightSearcherType type, FlightSearchQuery query) {
        List<ProviderFare> fares = new ArrayList<>();
        for (LocalDate date : query.dates()) {
            for (int flightIdx = 0; flightIdx < FLIGHTS_PER_DAY; flightIdx++) {
                String flightNumber = type.carrierCode() + (100 + flightIdx);
                BigDecimal flightBase = basePrice(query, date, flightNumber);
                double providerFactor = providerFactor(type.providerId(), flightNumber, date);
                for (int c = 0; c < PRICE_CLASSES.length; c++) {
                    BigDecimal amount = flightBase.multiply(BigDecimal.valueOf(CLASS_MULTIPLIER[c])).multiply(BigDecimal.valueOf(providerFactor)).setScale(2, RoundingMode.HALF_UP);
                    fares.add(new ProviderFare(type.providerId(), query.origin(), query.destination(), date, flightNumber, PRICE_CLASSES[c], amount));
                }
            }
        }
        return fares;
    }

    private List<ProviderFare> filterExcluded(List<ProviderFare> fares, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return fares;
        }
        return fares.stream().filter(f -> !excluded.contains(FareKeys.of(f))).toList();
    }

    /**
     * Deterministic per-flight base price so results are reproducible.
     */
    private BigDecimal basePrice(FlightSearchQuery query, LocalDate date, String flightNumber) {
        Random rng = new Random(Objects.hash(query.origin(), query.destination(), date, flightNumber));
        double base = 60 + rng.nextInt(180); // 60..239 in base currency
        return BigDecimal.valueOf(base);
    }

    /**
     * Each provider applies its own deterministic factor so the same flight has different prices per carrier.
     */
    private double providerFactor(String providerId, String flightNumber, LocalDate date) {
        Random rng = new Random(Objects.hash(providerId, flightNumber, date));
        return 0.90 + rng.nextDouble() * 0.25; // 0.90..1.15
    }

    private void simulateLatency(ProviderSettings settings) {
        long min = settings.getMinLatencyMillis();
        long max = Math.max(min, settings.getMaxLatencyMillis());
        long delay = (max == min) ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while contacting provider", e);
        }
    }

    private void simulateFailure(FlightSearcherType type, ProviderSettings settings) {
        if (settings.getFailureRate() > 0 && ThreadLocalRandom.current().nextDouble() < settings.getFailureRate()) {
            throw new IllegalStateException("Simulated upstream failure from " + type.providerId());
        }
    }
}
