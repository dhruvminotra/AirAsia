package com.airasia.flight.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Emitted when a specific price class on a flight sells out (from the booking
 * flow or the listing page). Drives asynchronous re-aggregation of the affected
 * (route, date) so the calendar never shows an unbookable fare.
 *
 * <p>{@code eventId} powers idempotency (duplicate delivery) and
 * {@code occurredAtEpochMillis} powers ordering (late / out-of-order delivery).
 */
public record SoldOutEvent(
        @NotBlank String eventId,
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull LocalDate date,
        @NotBlank String flightNumber,
        @NotBlank String priceClass,
        long occurredAtEpochMillis) {
}
