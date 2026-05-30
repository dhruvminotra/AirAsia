package com.airasia.flight.currency;

import com.airasia.flight.config.CalendarProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Config-backed rates ({@code calendar.exchange-rates}). Adding a currency is a
 * pure data change — no new class, no edit to conversion logic. The base
 * currency maps to 1.0.
 */
@Component
public class InMemoryExchangeRateProvider implements ExchangeRateProvider {

    private final Map<String, BigDecimal> rates = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public InMemoryExchangeRateProvider(CalendarProperties properties) {
        rates.put(properties.getBaseCurrency().toUpperCase(Locale.ROOT), BigDecimal.ONE);
        properties.getExchangeRates().forEach((code, rate) -> rates.put(code.toUpperCase(Locale.ROOT), rate));
    }

    @Override
    public Optional<BigDecimal> rateFor(String currency) {
        return Optional.ofNullable(rates.get(currency.toUpperCase(Locale.ROOT)));
    }

    @Override
    public boolean supports(String currency) {
        return rates.containsKey(currency.toUpperCase(Locale.ROOT));
    }
}
