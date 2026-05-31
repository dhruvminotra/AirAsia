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
import com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Consumes {@code price-class-sold-out} events and asynchronously refreshes the
 * affected (route,date) so the calendar never shows an unbookable fare.
 * Re-aggregates with the sold-out fare excluded, picks the next lowest, and
 * overwrites the cache. Idempotent and order-aware (see {@link ProcessedEventStore}).
 */
@Component
@DependsOn("pubSubBootstrap")
public class SoldOutEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SoldOutEventSubscriber.class);

    private final PubSubTemplate pubSubTemplate;
    private final FlightSearchEngine aggregator;
    private final LowFareCache cache;
    private final ProcessedEventStore processedEvents;
    private final String subscription;

    public SoldOutEventSubscriber(PubSubTemplate pubSubTemplate, FlightSearchEngine aggregator,
                                  LowFareCache cache, ProcessedEventStore processedEvents,
                                  CalendarProperties properties) {
        this.pubSubTemplate = pubSubTemplate;
        this.aggregator = aggregator;
        this.cache = cache;
        this.processedEvents = processedEvents;
        this.subscription = properties.pubsub().soldOutSubscription();
    }

    @PostConstruct
    void subscribe() {
        log.info("Subscribing to sold-out subscription '{}'", subscription);
        pubSubTemplate.subscribeAndConvert(subscription, this::onMessage, SoldOutEvent.class);
    }

    void onMessage(ConvertedBasicAcknowledgeablePubsubMessage<SoldOutEvent> message) {
        SoldOutEvent event = message.getPayload();
        try {
            handle(event);
            message.ack();
        } catch (RuntimeException e) {
            // Negative-ack so Pub/Sub redelivers; processing is idempotent.
            log.error("Failed to handle sold-out event {}: {}", event.eventId(), e.getMessage());
            message.nack();
        }
    }

    /** Visible for testing. */
    void handle(SoldOutEvent event) {
        if (processedEvents.isDuplicate(event.eventId())) {
            log.debug("Skipping duplicate sold-out event {}", event.eventId());
            return;
        }
        if (!processedEvents.isNewerThanApplied(event.origin(), event.destination(),
                event.date(), event.occurredAtEpochMillis())) {
            log.debug("Skipping out-of-order sold-out event {} (older than last applied)", event.eventId());
            return;
        }

        Set<String> excluded = Set.of(FareKeys.of(event.flightNumber(), event.priceClass(), event.date()));
        Map<LocalDate, ProviderFare> fresh = aggregator.lowestByDate(
                FlightSearchQuery.forSingleDate(event.origin(), event.destination(), event.date(), excluded));

        ProviderFare next = fresh.get(event.date());
        CachedLowFare updated = next != null ? CachedLowFare.of(next) : CachedLowFare.empty(event.date());
        cache.put(event.origin(), event.destination(), updated);

        processedEvents.recordApplied(event.origin(), event.destination(),
                event.date(), event.occurredAtEpochMillis());
        processedEvents.markProcessed(event.eventId());
        log.info("Updated low fare for {}-{} {} -> {}", event.origin(), event.destination(),
                event.date(), updated.empty() ? "UNAVAILABLE" : updated.baseAmount());
    }
}
