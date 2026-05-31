package com.airasia.flight.service;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.FareCalendarRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * Pre-populates the cache for popular routes on startup so the first real users
 * don't pay the cold-miss penalty. Runs asynchronously to avoid delaying boot,
 * and reuses the normal read path so warming is identical to a live request.
 */
@Service
public class CacheWarmingService {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmingService.class);

    private final CalendarService calendarService;
    private final CalendarProperties.Warming warming;
    private final String baseCurrency;

    public CacheWarmingService(CalendarService calendarService, CalendarProperties properties) {
        this.calendarService = calendarService;
        this.warming = properties.warming();
        this.baseCurrency = properties.baseCurrency();
    }

    @Async("providerExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (!warming.enabled() || warming.routes().isEmpty()) {
            return;
        }
        log.info("Warming cache for {} route(s), {} month(s) ahead", warming.routes().size(), warming.monthsAhead());
        for (String route : warming.routes()) {
            String[] parts = route.split("-");
            if (parts.length != 2) {
                log.warn("Skipping malformed warming route '{}'", route);
                continue;
            }
            for (int offset = 0; offset <= warming.monthsAhead(); offset++) {
                YearMonth month = YearMonth.now().plusMonths(offset);
                warmQuietly(parts[0], parts[1], month);
            }
        }
        log.info("Cache warming complete");
    }

    private void warmQuietly(String origin, String destination, YearMonth month) {
        try {
            calendarService.getCalendar(FareCalendarRequest.of(origin, destination, month, baseCurrency));
        } catch (RuntimeException e) {
            log.warn("Warming failed for {}-{} {}: {}", origin, destination, month, e.getMessage());
        }
    }
}
