package com.airasia.flight.model;

import java.util.List;

public record FareCalendarResponse(
        String origin,
        String destination,
        String month,
        String currency,
        List<FareCalendarData> days) {
}
