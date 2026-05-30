package com.airasia.flight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds the {@code calendar.*} block. Exchange rates and cache tuning live here
 * so they are data-driven: supporting a new currency is a config edit, not a
 * code change (Open/Closed Principle).
 */
@ConfigurationProperties(prefix = "calendar")
public class CalendarProperties {

    private String baseCurrency = "MYR";
    private long providerTimeoutMillis = 450;
    private Map<String, BigDecimal> exchangeRates = new LinkedHashMap<>();
    private Cache cache = new Cache();
    private PubSub pubsub = new PubSub();
    private Warming warming = new Warming();

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public long getProviderTimeoutMillis() {
        return providerTimeoutMillis;
    }

    public void setProviderTimeoutMillis(long providerTimeoutMillis) {
        this.providerTimeoutMillis = providerTimeoutMillis;
    }

    public Map<String, BigDecimal> getExchangeRates() {
        return exchangeRates;
    }

    public void setExchangeRates(Map<String, BigDecimal> exchangeRates) {
        this.exchangeRates = exchangeRates;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public PubSub getPubsub() {
        return pubsub;
    }

    public void setPubsub(PubSub pubsub) {
        this.pubsub = pubsub;
    }

    public Warming getWarming() {
        return warming;
    }

    public void setWarming(Warming warming) {
        this.warming = warming;
    }

    public static class Cache {
        private long ttlSeconds = 86400;
        private long ttlJitterSeconds = 3600;
        private long emptyTtlSeconds = 300;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public long getTtlJitterSeconds() {
            return ttlJitterSeconds;
        }

        public void setTtlJitterSeconds(long ttlJitterSeconds) {
            this.ttlJitterSeconds = ttlJitterSeconds;
        }

        public long getEmptyTtlSeconds() {
            return emptyTtlSeconds;
        }

        public void setEmptyTtlSeconds(long emptyTtlSeconds) {
            this.emptyTtlSeconds = emptyTtlSeconds;
        }
    }

    public static class Warming {
        private boolean enabled = true;
        private int monthsAhead = 1;
        /** Routes to pre-warm, formatted "ORIGIN-DESTINATION". */
        private List<String> routes = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMonthsAhead() {
            return monthsAhead;
        }

        public void setMonthsAhead(int monthsAhead) {
            this.monthsAhead = monthsAhead;
        }

        public List<String> getRoutes() {
            return routes;
        }

        public void setRoutes(List<String> routes) {
            this.routes = routes;
        }
    }

    public static class PubSub {
        private String soldOutTopic = "price-class-sold-out";
        private String soldOutSubscription = "sold-out-sub";

        public String getSoldOutTopic() {
            return soldOutTopic;
        }

        public void setSoldOutTopic(String soldOutTopic) {
            this.soldOutTopic = soldOutTopic;
        }

        public String getSoldOutSubscription() {
            return soldOutSubscription;
        }

        public void setSoldOutSubscription(String soldOutSubscription) {
            this.soldOutSubscription = soldOutSubscription;
        }
    }
}
