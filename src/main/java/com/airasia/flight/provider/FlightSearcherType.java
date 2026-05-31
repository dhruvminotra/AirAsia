package com.airasia.flight.provider;

/**
 * Enumerates every AirAsia Group carrier the calendar aggregates over. Each
 * entry carries the values that <i>identify</i> a carrier — the stable provider
 * id (used for the circuit breaker instance + metrics tag), the human-friendly
 * display name, and the IATA carrier code (used to number the flights in the
 * mock). Tunables that change between environments (latency, failure rate) live
 * in {@code application.yml} under {@code providers.*} and are looked up by
 * {@link #providerId()} via {@link ProviderProperties}.
 *
 * <p>Adding a fourth carrier is one new enum value here + one yaml block — no
 * new class is required (all carriers share a single {@link NavitaireSearcher}).
 */
public enum FlightSearcherType {

    AIRASIA_MALAYSIA("airasia-malaysia", "AirAsia (Malaysia)", "AK"),
    AIRASIA_X("airasia-x", "AirAsia X", "D7"),
    THAI_AIRASIA("thai-airasia", "Thai AirAsia", "FD");

    private final String providerId;
    private final String displayName;
    private final String carrierCode;

    FlightSearcherType(String providerId, String displayName, String carrierCode) {
        this.providerId = providerId;
        this.displayName = displayName;
        this.carrierCode = carrierCode;
    }

    /**
     * Stable id; also the Resilience4j circuit-breaker instance name + metrics tag.
     */
    public String providerId() {
        return providerId;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * IATA code used for flight numbers (e.g. AK101, D7100, FD102).
     */
    public String carrierCode() {
        return carrierCode;
    }
}
