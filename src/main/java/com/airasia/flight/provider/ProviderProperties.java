package com.airasia.flight.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code providers.*} block — one entry per AirAsia Group carrier
 * (each running its own Navitaire New Skies). Each carrier's IATA code and its
 * simulated latency/failure rate are config, not code, so behaviour can be tuned
 * (e.g. to exercise the circuit breaker) without recompiling.
 */
@ConfigurationProperties(prefix = "providers")
public class ProviderProperties {

    private ProviderSettings airAsiaMalaysia = new ProviderSettings();
    private ProviderSettings airAsiaX = new ProviderSettings();
    private ProviderSettings thaiAirAsia = new ProviderSettings();

    public ProviderSettings getAirAsiaMalaysia() {
        return airAsiaMalaysia;
    }

    public void setAirAsiaMalaysia(ProviderSettings airAsiaMalaysia) {
        this.airAsiaMalaysia = airAsiaMalaysia;
    }

    public ProviderSettings getAirAsiaX() {
        return airAsiaX;
    }

    public void setAirAsiaX(ProviderSettings airAsiaX) {
        this.airAsiaX = airAsiaX;
    }

    public ProviderSettings getThaiAirAsia() {
        return thaiAirAsia;
    }

    public void setThaiAirAsia(ProviderSettings thaiAirAsia) {
        this.thaiAirAsia = thaiAirAsia;
    }

    public static class ProviderSettings {
        private String displayName = "provider";
        private String carrierCode = "AK";
        private long minLatencyMillis = 20;
        private long maxLatencyMillis = 120;
        private double failureRate = 0.0;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getCarrierCode() {
            return carrierCode;
        }

        public void setCarrierCode(String carrierCode) {
            this.carrierCode = carrierCode;
        }

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
