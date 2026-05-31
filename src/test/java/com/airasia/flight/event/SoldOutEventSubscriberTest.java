package com.airasia.flight.event;

import com.airasia.flight.aggregation.FlightSearchEngine;
import com.airasia.flight.cache.LowFareCache;
import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.CachedLowFare;
import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.model.SoldOutEvent;
import com.airasia.flight.provider.FareKeys;
import com.airasia.flight.provider.FlightSearchQuery;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoldOutEventSubscriberTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 15);

    private FlightSearchEngine aggregator;
    private LowFareCache cache;
    private ProcessedEventStore processedEvents;
    private SoldOutEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        aggregator = mock(FlightSearchEngine.class);
        cache = mock(LowFareCache.class);
        processedEvents = mock(ProcessedEventStore.class);
        subscriber = new SoldOutEventSubscriber(mock(PubSubTemplate.class), aggregator, cache,
                processedEvents, CalendarProperties.defaults());
    }

    private SoldOutEvent event(String id, long ts) {
        return new SoldOutEvent(id, "KUL", "SIN", DATE, "AK100", "PROMO", ts);
    }

    @Test
    void recomputesNextLowestExcludingSoldOutFare() {
        when(processedEvents.isDuplicate("e1")).thenReturn(false);
        when(processedEvents.isNewerThanApplied(eq("KUL"), eq("SIN"), eq(DATE), eq(1000L))).thenReturn(true);
        when(aggregator.lowestByDate(any())).thenReturn(Map.of(DATE,
                new ProviderFare("thai-airasia", "KUL", "SIN", DATE, "FD101", "SAVER", new BigDecimal("75.00"))));

        subscriber.handle(event("e1", 1000L));

        ArgumentCaptor<FlightSearchQuery> commandCaptor = ArgumentCaptor.forClass(FlightSearchQuery.class);
        verify(aggregator).lowestByDate(commandCaptor.capture());
        assertThat(commandCaptor.getValue().excludedFareKeys())
                .containsExactly(FareKeys.of("AK100", "PROMO", DATE));

        ArgumentCaptor<CachedLowFare> fareCaptor = ArgumentCaptor.forClass(CachedLowFare.class);
        verify(cache).put(eq("KUL"), eq("SIN"), fareCaptor.capture());
        assertThat(fareCaptor.getValue().baseAmount()).isEqualByComparingTo("75.00");
        assertThat(fareCaptor.getValue().empty()).isFalse();

        verify(processedEvents).recordApplied("KUL", "SIN", DATE, 1000L);
        verify(processedEvents).markProcessed("e1");
    }

    @Test
    void ignoresDuplicateEvent() {
        when(processedEvents.isDuplicate("dup")).thenReturn(true);

        subscriber.handle(event("dup", 1000L));

        verify(aggregator, never()).lowestByDate(any());
        verify(cache, never()).put(any(), any(), any());
        verify(processedEvents, never()).markProcessed(any());
    }

    @Test
    void ignoresOutOfOrderEvent() {
        when(processedEvents.isDuplicate("late")).thenReturn(false);
        when(processedEvents.isNewerThanApplied(any(), any(), any(), eq(500L))).thenReturn(false);

        subscriber.handle(event("late", 500L));

        verify(aggregator, never()).lowestByDate(any());
        verify(cache, never()).put(any(), any(), any());
        verify(processedEvents, never()).markProcessed(any());
    }

    @Test
    void marksDateUnavailableWhenNoFareRemains() {
        when(processedEvents.isDuplicate(any())).thenReturn(false);
        when(processedEvents.isNewerThanApplied(any(), any(), any(), anyLong())).thenReturn(true);
        when(aggregator.lowestByDate(any())).thenReturn(Map.of());

        subscriber.handle(event("e2", 2000L));

        ArgumentCaptor<CachedLowFare> fareCaptor = ArgumentCaptor.forClass(CachedLowFare.class);
        verify(cache, times(1)).put(eq("KUL"), eq("SIN"), fareCaptor.capture());
        assertThat(fareCaptor.getValue().empty()).isTrue();
    }
}
