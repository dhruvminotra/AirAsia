package com.airasia.flight.currency;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts base-currency amounts (as stored in the cache) into the currency the
 * user requested. Conversion happens at read time, which keeps the cache
 * currency-agnostic: one cached value serves every currency.
 */
@Service
public class CurrencyConversionService {

    private final ExchangeRateProvider rateProvider;

    public CurrencyConversionService(ExchangeRateProvider rateProvider) {
        this.rateProvider = rateProvider;
    }

    public BigDecimal convertFromBase(BigDecimal baseAmount, String targetCurrency) {
        if (baseAmount == null) {
            return null;
        }
        BigDecimal rate = rateProvider.rateFor(targetCurrency)
                .orElseThrow(() -> new UnsupportedCurrencyException(targetCurrency));
        return baseAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isSupported(String currency) {
        return rateProvider.supports(currency);
    }
}
