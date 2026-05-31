package com.airasia.flight.event;

import com.airasia.flight.config.CalendarProperties;
import com.airasia.flight.model.SoldOutEvent;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes sold-out events to Pub/Sub (JSON via the Jackson converter). In
 * production this is invoked by the booking/listing services; here it is also
 * driven by a test endpoint to demonstrate the async update path.
 */
@Component
public class SoldOutEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SoldOutEventPublisher.class);

    private final PubSubTemplate pubSubTemplate;
    private final String topic;

    public SoldOutEventPublisher(PubSubTemplate pubSubTemplate, CalendarProperties properties) {
        this.pubSubTemplate = pubSubTemplate;
        this.topic = properties.pubsub().soldOutTopic();
    }

    public void publish(SoldOutEvent event) {
        log.info("Publishing sold-out event {} for {}-{} {} flight {} class {}", event.eventId(),
                event.origin(), event.destination(), event.date(), event.flightNumber(), event.priceClass());
        pubSubTemplate.publish(topic, event);
    }
}
