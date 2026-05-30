package com.airasia.flight.calendar;

import com.airasia.flight.event.SoldOutEventPublisher;
import com.airasia.flight.model.FareCalendarRequest;
import com.airasia.flight.model.FareCalendarResponse;
import com.airasia.flight.model.SoldOutEvent;
import com.airasia.flight.service.CalendarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * REST entry point for the low fare calendar. Thin: it validates/normalises the
 * request and delegates to {@link CalendarService} (read) or the
 * {@link SoldOutEventPublisher} (async write).
 */
@RestController
@RequestMapping("/api/v1/flights")
@Validated
public class FlightCalendarController {

    private final CalendarService calendarService;
    private final SoldOutEventPublisher soldOutEventPublisher;

    public FlightCalendarController(CalendarService calendarService, SoldOutEventPublisher soldOutEventPublisher) {
        this.calendarService = calendarService;
        this.soldOutEventPublisher = soldOutEventPublisher;
    }

    @GetMapping("/calendar")
    public FareCalendarResponse getCalendar(@RequestParam @NotBlank String origin, @RequestParam @NotBlank String destination, @RequestParam @NotBlank String month, @RequestParam(defaultValue = "MYR") String currency) {

        FareCalendarRequest query = FareCalendarRequest.of(origin, destination, parseMonth(month), currency);
        return calendarService.getCalendar(query);
    }

    /**
     * Test/ops hook to publish a sold-out event onto Pub/Sub, proving the async
     * update path. In production these events originate from the booking/listing
     * services, not this endpoint.
     */
    @PostMapping("/sold-out")
    public ResponseEntity<Void> publishSoldOut(@Valid @RequestBody SoldOutEvent event) {
        soldOutEventPublisher.publish(event);
        return ResponseEntity.accepted().build();
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid month '" + month + "', expected format yyyy-MM");
        }
    }
}
