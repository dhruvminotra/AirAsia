package com.airasia.flight.currency;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Source of FX rates expressed as: 1 unit of base currency = rate units of the
 * target currency. Abstracted behind an interface so the in-memory/config-backed
 * implementation can later be swapped for a live FX feed with no caller changes.
 */
public interface ExchangeRateProvider {

    /** Rate to convert FROM the base currency TO {@code currency}. */
    Optional<BigDecimal> rateFor(String currency);

    boolean supports(String currency);
}
