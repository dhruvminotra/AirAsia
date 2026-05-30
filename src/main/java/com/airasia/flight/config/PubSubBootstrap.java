package com.airasia.flight.config;

import com.google.cloud.spring.pubsub.PubSubAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates the sold-out topic and subscription on startup if they don't already
 * exist (idempotent), so a fresh emulator works with no manual gcloud steps.
 * The subscriber {@code @DependsOn} this bean to guarantee ordering.
 */
@Component("pubSubBootstrap")
public class PubSubBootstrap {

    private static final Logger log = LoggerFactory.getLogger(PubSubBootstrap.class);

    public PubSubBootstrap(PubSubAdmin pubSubAdmin, CalendarProperties properties) {
        String topic = properties.getPubsub().getSoldOutTopic();
        String subscription = properties.getPubsub().getSoldOutSubscription();

        if (pubSubAdmin.getTopic(topic) == null) {
            pubSubAdmin.createTopic(topic);
            log.info("Created Pub/Sub topic '{}'", topic);
        }
        if (pubSubAdmin.getSubscription(subscription) == null) {
            pubSubAdmin.createSubscription(subscription, topic);
            log.info("Created Pub/Sub subscription '{}' on topic '{}'", subscription, topic);
        }
    }
}
