package com.airasia.flight.aggregation;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.FlightSearchQuery;
import com.airasia.flight.provider.FlightSearcherType;
import com.airasia.flight.provider.NavitaireSearcher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlightSearchEngineTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 10);

    private NavitaireSearcher searcher;

    @BeforeEach
    void setUp() {
        searcher = mock(NavitaireSearcher.class);
    }

    private FlightSearchEngine engine(long timeoutMillis) {
        CalendarProperties properties = CalendarProperties.defaults()
                .withProviderTimeoutMillis(timeoutMillis);
        return new FlightSearchEngine(searcher,
                Executors.newCachedThreadPool(),
                CircuitBreakerRegistry.ofDefaults(),
                new SimpleMeterRegistry(),
                properties);
    }

    private ProviderFare fare(FlightSearcherType type, BigDecimal amount, String priceClass) {
        return new ProviderFare(type.providerId(), "KUL", "SIN", DATE,
                type.carrierCode() + "100", priceClass, amount);
    }

    @Test
    void picksLowestPriceAcrossCarriers() {
        when(searcher.search(eq(FlightSearcherType.AIRASIA_MALAYSIA), any()))
                .thenReturn(List.of(fare(FlightSearcherType.AIRASIA_MALAYSIA, new BigDecimal("100.00"), "SAVER")));
        when(searcher.search(eq(FlightSearcherType.AIRASIA_X), any()))
                .thenReturn(List.of(fare(FlightSearcherType.AIRASIA_X, new BigDecimal("80.00"), "PROMO")));
        when(searcher.search(eq(FlightSearcherType.THAI_AIRASIA), any()))
                .thenReturn(List.of(fare(FlightSearcherType.THAI_AIRASIA, new BigDecimal("120.00"), "FLEX")));

        Map<LocalDate, ProviderFare> result = engine(1000)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        assertThat(result.get(DATE).baseAmount()).isEqualByComparingTo("80.00");
        assertThat(result.get(DATE).provider()).isEqualTo(FlightSearcherType.AIRASIA_X.providerId());
    }

    @Test
    void degradesGracefullyWhenOneCarrierFails() {
        when(searcher.search(eq(FlightSearcherType.AIRASIA_MALAYSIA), any()))
                .thenReturn(List.of(fare(FlightSearcherType.AIRASIA_MALAYSIA, new BigDecimal("150.00"), "SAVER")));
        when(searcher.search(eq(FlightSearcherType.AIRASIA_X), any()))
                .thenThrow(new IllegalStateException("provider down"));
        when(searcher.search(eq(FlightSearcherType.THAI_AIRASIA), any()))
                .thenReturn(List.of(fare(FlightSearcherType.THAI_AIRASIA, new BigDecimal("90.00"), "PROMO")));

        Map<LocalDate, ProviderFare> result = engine(1000)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        assertThat(result.get(DATE).baseAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void returnsNoFareWhenAllCarriersFail() {
        when(searcher.search(any(), any())).thenThrow(new IllegalStateException("down"));

        Map<LocalDate, ProviderFare> result = engine(1000)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        assertThat(result).doesNotContainKey(DATE);
    }

    @Test
    void abandonsSlowCarrierViaTimeoutButKeepsFastOnes() {
        // The slow (cheaper) carrier deliberately sleeps past the timeout.
        when(searcher.search(eq(FlightSearcherType.AIRASIA_MALAYSIA), any())).thenAnswer(inv -> {
            sleep(500);
            return List.of(fare(FlightSearcherType.AIRASIA_MALAYSIA, new BigDecimal("10.00"), "PROMO"));
        });
        when(searcher.search(eq(FlightSearcherType.AIRASIA_X), any()))
                .thenReturn(List.of(fare(FlightSearcherType.AIRASIA_X, new BigDecimal("70.00"), "SAVER")));
        when(searcher.search(eq(FlightSearcherType.THAI_AIRASIA), any()))
                .thenReturn(List.of(fare(FlightSearcherType.THAI_AIRASIA, new BigDecimal("85.00"), "SAVER")));

        Map<LocalDate, ProviderFare> result = engine(120)
                .lowestByDate(FlightSearchQuery.forDates("KUL", "SIN", List.of(DATE)));

        // The slow (cheaper) carrier is timed out → AirAsia X (70) wins over Thai (85).
        assertThat(result.get(DATE).provider()).isEqualTo(FlightSearcherType.AIRASIA_X.providerId());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
