package com.airasia.flight.service;

import com.airasia.flight.aggregation.FlightSearchEngine;
import com.airasia.flight.cache.LowFareCache;
import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.cache.RequestCoalescer;
import com.airasia.flight.currency.CurrencyConversionService;
import com.airasia.flight.currency.InMemoryExchangeRateProvider;
import com.airasia.flight.currency.UnsupportedCurrencyException;
import com.airasia.flight.model.CachedLowFare;
import com.airasia.flight.model.FareCalendarRequest;
import com.airasia.flight.model.FareCalendarResponse;
import com.airasia.flight.model.ProviderFare;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarServiceTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 6);

    private LowFareCache cache;
    private FlightSearchEngine aggregator;
    private CalendarService service;

    @BeforeEach
    void setUp() {
        cache = mock(LowFareCache.class);
        aggregator = mock(FlightSearchEngine.class);

        CalendarProperties properties = new CalendarProperties();
        properties.setBaseCurrency("MYR");
        properties.setExchangeRates(Map.of("USD", new BigDecimal("0.20")));
        CurrencyConversionService currency =
                new CurrencyConversionService(new InMemoryExchangeRateProvider(properties));

        service = new CalendarService(cache, aggregator, currency, new RequestCoalescer());
    }

    @Test
    void servesFromCacheWithoutCallingProvidersOnFullHit() {
        Map<LocalDate, CachedLowFare> cached = new LinkedHashMap<>();
        datesOf(MONTH).forEach(d -> cached.put(d,
                new CachedLowFare(d, new BigDecimal("100.00"), "airasia-malaysia", "AK100", "PROMO", false)));
        when(cache.getAll(any(), any(), anyList())).thenReturn(cached);

        FareCalendarResponse response = service.getCalendar(FareCalendarRequest.of("kul", "sin", MONTH, "USD"));

        verify(aggregator, never()).lowestByDate(any());
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.days()).hasSize(MONTH.lengthOfMonth());
        // 100 base * 0.20 = 20.00 in USD
        assertThat(response.days().get(0).price()).isEqualByComparingTo("20.00");
        assertThat(response.days().get(0).available()).isTrue();
    }

    @Test
    void aggregatesAndCachesOnMiss() {
        when(cache.getAll(any(), any(), anyList())).thenReturn(Map.of());
        Map<LocalDate, ProviderFare> fresh = new LinkedHashMap<>();
        datesOf(MONTH).forEach(d -> fresh.put(d,
                new ProviderFare("airasia-x", "KUL", "SIN", d, "D7200", "PROMO", new BigDecimal("55.00"))));
        when(aggregator.lowestByDate(any())).thenReturn(fresh);

        FareCalendarResponse response = service.getCalendar(FareCalendarRequest.of("KUL", "SIN", MONTH, "MYR"));

        verify(aggregator, times(1)).lowestByDate(any());
        verify(cache, times(MONTH.lengthOfMonth())).put(any(), any(), any());
        assertThat(response.days().get(0).price()).isEqualByComparingTo("55.00");
    }

    @Test
    void marksDatesWithoutInventoryAsUnavailable() {
        when(cache.getAll(any(), any(), anyList())).thenReturn(Map.of());
        when(aggregator.lowestByDate(any())).thenReturn(Map.of()); // no provider had fares

        FareCalendarResponse response = service.getCalendar(FareCalendarRequest.of("KUL", "SIN", MONTH, "MYR"));

        assertThat(response.days()).allSatisfy(day -> {
            assertThat(day.available()).isFalse();
            assertThat(day.price()).isNull();
        });
        // Empty markers should still be cached (negative cache) to spare providers.
        verify(cache, times(MONTH.lengthOfMonth())).put(any(), any(), any());
    }

    @Test
    void rejectsUnsupportedCurrencyBeforeTouchingCache() {
        assertThatThrownBy(() -> service.getCalendar(FareCalendarRequest.of("KUL", "SIN", MONTH, "JPY")))
                .isInstanceOf(UnsupportedCurrencyException.class);
        verify(cache, never()).getAll(any(), any(), anyList());
    }

    private List<LocalDate> datesOf(YearMonth month) {
        return java.util.stream.IntStream.rangeClosed(1, month.lengthOfMonth())
                .mapToObj(month::atDay)
                .toList();
    }
}
