package com.airasia.flight.aggregation;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.AbstractFlightSearcher;
import com.airasia.flight.provider.FlightSearchQuery;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class FlightSearchEngineTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 10);

    private FlightSearchEngine engine(List<AbstractFlightSearcher> providers, long timeoutMillis) {
        CalendarProperties properties = CalendarProperties.defaults()
                .withProviderTimeoutMillis(timeoutMillis);
        return new FlightSearchEngine(providers,
                Executors.newCachedThreadPool(),
                CircuitBreakerRegistry.ofDefaults(),
                new SimpleMeterRegistry(),
                properties);
    }

    private ProviderFare fare(String provider, BigDecimal amount, String priceClass) {
        return new ProviderFare(provider, "KUL", "SIN", DATE, "AK100", priceClass, amount);
    }

    @Test
    void picksLowestPriceAcrossProviders() {
        List<AbstractFlightSearcher> providers = List.of(
                fakeProvider("sabre",   () -> List.of(fare("sabre",   new BigDecimal("100.00"), "SAVER"))),
                fakeProvider("amadeus", () -> List.of(fare("amadeus", new BigDecimal("80.00"),  "PROMO"))),
                fakeProvider("galileo", () -> List.of(fare("galileo", new BigDecimal("120.00"), "FLEX"))));

        Map<LocalDate, ProviderFare> result = engine(providers, 1000)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        assertThat(result.get(DATE).baseAmount()).isEqualByComparingTo("80.00");
        assertThat(result.get(DATE).provider()).isEqualTo("amadeus");
    }

    @Test
    void degradesGracefullyWhenOneProviderFails() {
        List<AbstractFlightSearcher> providers = List.of(
                fakeProvider("sabre",   () -> List.of(fare("sabre",   new BigDecimal("150.00"), "SAVER"))),
                fakeProvider("amadeus", () -> { throw new IllegalStateException("provider down"); }),
                fakeProvider("galileo", () -> List.of(fare("galileo", new BigDecimal("90.00"),  "PROMO"))));

        Map<LocalDate, ProviderFare> result = engine(providers, 1000)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        assertThat(result.get(DATE).baseAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void returnsNoFareWhenAllProvidersFail() {
        List<AbstractFlightSearcher> providers = List.of(
                fakeProvider("sabre",   () -> { throw new IllegalStateException("down"); }),
                fakeProvider("amadeus", () -> { throw new IllegalStateException("down"); }),
                fakeProvider("galileo", () -> { throw new IllegalStateException("down"); }));

        Map<LocalDate, ProviderFare> result = engine(providers, 1000)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        assertThat(result).doesNotContainKey(DATE);
    }

    @Test
    void abandonsSlowProviderViaTimeoutButKeepsFastOnes() {
        List<AbstractFlightSearcher> providers = List.of(
                fakeProvider("slow", () -> {
                    sleep(500);
                    return List.of(fare("slow", new BigDecimal("10.00"), "PROMO"));
                }),
                fakeProvider("fast", () -> List.of(fare("fast", new BigDecimal("70.00"), "SAVER"))));

        Map<LocalDate, ProviderFare> result = engine(providers, 120)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        assertThat(result.get(DATE).provider()).isEqualTo("fast");
    }

    private AbstractFlightSearcher fakeProvider(String id, Supplier<List<ProviderFare>> behaviour) {
        return new AbstractFlightSearcher() {
            @Override public String providerId() { return id; }
            @Override public String displayName() { return id; }
            @Override protected List<ProviderFare> doSearch(FlightSearchQuery query) { return behaviour.get(); }
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
