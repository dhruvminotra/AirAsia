package com.airasia.flight.service;

import com.airasia.flight.aggregation.FlightSearchEngine;
import com.airasia.flight.cache.LowFareCache;
import com.airasia.flight.cache.RequestCoalescer;
import com.airasia.flight.currency.CurrencyConversionService;
import com.airasia.flight.currency.UnsupportedCurrencyException;
import com.airasia.flight.model.CachedLowFare;
import com.airasia.flight.model.FareCalendarRequest;
import com.airasia.flight.model.FareCalendarResponse;
import com.airasia.flight.model.FareCalendarData;
import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.FlightSearchQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the calendar read path: validate currency → cache-aside read →
 * coalesced miss loading (thundering-herd safe) → scatter-gather aggregation →
 * currency conversion. The cache holds base-currency prices; conversion is
 * applied last so one cached value serves every currency.
 */
@Service
public class CalendarService {

    private final LowFareCache cache;
    private final FlightSearchEngine aggregator;
    private final CurrencyConversionService currencyConversion;
    private final RequestCoalescer coalescer;

    public CalendarService(LowFareCache cache, FlightSearchEngine aggregator,
                           CurrencyConversionService currencyConversion, RequestCoalescer coalescer) {
        this.cache = cache;
        this.aggregator = aggregator;
        this.currencyConversion = currencyConversion;
        this.coalescer = coalescer;
    }

    public FareCalendarResponse getCalendar(FareCalendarRequest query) {
        if (!currencyConversion.isSupported(query.currency())) {
            throw new UnsupportedCurrencyException(query.currency());
        }

        List<LocalDate> dates = datesOf(query);
        Map<LocalDate, CachedLowFare> fares = new LinkedHashMap<>(
                cache.getAll(query.origin(), query.destination(), dates));

        List<LocalDate> missing = dates.stream().filter(d -> !fares.containsKey(d)).toList();
        if (!missing.isEmpty()) {
            // Single-flight per route+month: concurrent misses for the same key
            // trigger only one provider fan-out (thundering-herd protection).
            String coalesceKey = "build:" + query.route() + ":" + query.month();
            Map<LocalDate, CachedLowFare> loaded =
                    coalescer.compute(coalesceKey, () -> computeAndStore(query, missing));
            fares.putAll(loaded);
        }

        return toResponse(query, dates, fares);
    }

    private Map<LocalDate, CachedLowFare> computeAndStore(FareCalendarRequest query, List<LocalDate> missing) {
        Map<LocalDate, ProviderFare> fresh = aggregator.lowestByDate(
                FlightSearchQuery.forDates(query.origin(), query.destination(), missing));

        Map<LocalDate, CachedLowFare> result = new LinkedHashMap<>();
        for (LocalDate date : missing) {
            ProviderFare fare = fresh.get(date);
            CachedLowFare cached = fare != null ? CachedLowFare.of(fare) : CachedLowFare.empty(date);
            cache.put(query.origin(), query.destination(), cached);
            result.put(date, cached);
        }
        return result;
    }

    private FareCalendarResponse toResponse(FareCalendarRequest query, List<LocalDate> dates,
                                        Map<LocalDate, CachedLowFare> fares) {
        List<FareCalendarData> days = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            CachedLowFare fare = fares.get(date);
            if (fare == null || fare.empty() || fare.baseAmount() == null) {
                days.add(FareCalendarData.unavailable(date));
            } else {
                days.add(new FareCalendarData(date,
                        currencyConversion.convertFromBase(fare.baseAmount(), query.currency()), true));
            }
        }
        return new FareCalendarResponse(query.origin(), query.destination(),
                query.month().toString(), query.currency(), days);
    }

    private List<LocalDate> datesOf(FareCalendarRequest query) {
        int length = query.month().lengthOfMonth();
        List<LocalDate> dates = new ArrayList<>(length);
        for (int day = 1; day <= length; day++) {
            dates.add(query.month().atDay(day));
        }
        return dates;
    }
}
