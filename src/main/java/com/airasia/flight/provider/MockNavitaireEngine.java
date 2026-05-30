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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for a real Navitaire integration ({@code NavitaireImpl} in our
 * production stack). It does <b>not</b> call any external system — it synthesises
 * deterministic fares so the design (aggregation, currency, caching, events) can
 * be demonstrated end to end, while simulating real-world latency and failures
 * to exercise timeouts and the circuit breaker.
 *
 * <p>Determinism note: <em>prices</em> are seeded from (route, date, flight,
 * provider) so results are reproducible and the per-provider minimum is stable;
 * only <em>latency</em> and <em>failures</em> use real randomness.
 */
@Component
public class MockNavitaireEngine {

    private static final int FLIGHTS_PER_DAY = 3;
    private static final String[] PRICE_CLASSES = {"PROMO", "SAVER", "FLEX"};
    private static final double[] CLASS_MULTIPLIER = {1.00, 1.35, 1.80};

    public List<ProviderFare> search(String providerId, ProviderSettings settings, FlightSearchQuery query) {
        simulateLatency(settings);
        simulateFailure(providerId, settings);

        List<ProviderFare> fares = new ArrayList<>();
        for (LocalDate date : query.dates()) {
            for (int flightIdx = 0; flightIdx < FLIGHTS_PER_DAY; flightIdx++) {
                String flightNumber = flightNumber(settings.getCarrierCode(), flightIdx);
                BigDecimal flightBase = basePrice(query, date, flightNumber);
                double providerFactor = providerFactor(providerId, flightNumber, date);
                for (int c = 0; c < PRICE_CLASSES.length; c++) {
                    BigDecimal amount = flightBase
                            .multiply(BigDecimal.valueOf(CLASS_MULTIPLIER[c]))
                            .multiply(BigDecimal.valueOf(providerFactor))
                            .setScale(2, RoundingMode.HALF_UP);
                    fares.add(new ProviderFare(providerId, query.origin(), query.destination(),
                            date, flightNumber, PRICE_CLASSES[c], amount));
                }
            }
        }
        return fares;
    }

    private String flightNumber(String carrierCode, int flightIdx) {
        // Each carrier numbers its own flights with its IATA code (e.g. AK100,
        // D7100, FD100). The calendar then picks the cheapest across all carriers
        // serving the route for a given date.
        return carrierCode + (100 + flightIdx);
    }

    private BigDecimal basePrice(FlightSearchQuery query, LocalDate date, String flightNumber) {
        Random rng = new Random(Objects.hash(query.origin(), query.destination(), date, flightNumber));
        double base = 60 + rng.nextInt(180); // 60..239 in base currency
        return BigDecimal.valueOf(base);
    }

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

    private void simulateFailure(String providerId, ProviderSettings settings) {
        if (settings.getFailureRate() > 0
                && ThreadLocalRandom.current().nextDouble() < settings.getFailureRate()) {
            throw new IllegalStateException("Simulated upstream failure from " + providerId);
        }
    }
}
