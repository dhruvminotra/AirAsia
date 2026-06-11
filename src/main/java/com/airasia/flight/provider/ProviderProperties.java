package com.airasia.flight.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds the {@code providers.*} block — one entry per GDS supplier
 * (Sabre, Amadeus, Galileo). Keys match {@link AbstractFlightSearcher#providerId()};
 * values are the simulated latency / failure rate tunables each searcher applies
 * to exercise the circuit breaker.
 */
@ConfigurationProperties(prefix = "providers")
public class ProviderProperties {

    /**
     * Spring-relaxed binding turns yaml keys like {@code airasia-malaysia} into this
     * map by direct key (no field-naming dance). The map is the property itself —
     * the field name is required for binding but no longer mirrors carrier names.
     */
    private Map<String, ProviderSettings> settings = new LinkedHashMap<>();

    public Map<String, ProviderSettings> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, ProviderSettings> settings) {
        this.settings = settings;
    }

    public ProviderSettings settingsFor(String providerId) {
        return settings.getOrDefault(providerId, new ProviderSettings());
    }

    public static class ProviderSettings {
        private long minLatencyMillis = 20;
        private long maxLatencyMillis = 120;
        private double failureRate = 0.0;

        public long getMinLatencyMillis() {
            return minLatencyMillis;
        }

        public void setMinLatencyMillis(long minLatencyMillis) {
            this.minLatencyMillis = minLatencyMillis;
        }

        public long getMaxLatencyMillis() {
            return maxLatencyMillis;
        }

        public void setMaxLatencyMillis(long maxLatencyMillis) {
            this.maxLatencyMillis = maxLatencyMillis;
        }

        public double getFailureRate() {
            return failureRate;
        }

        public void setFailureRate(double failureRate) {
            this.failureRate = failureRate;
        }
    }
}
