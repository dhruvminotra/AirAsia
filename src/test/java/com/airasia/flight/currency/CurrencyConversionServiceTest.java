package com.airasia.flight.currency;

import com.airasia.flight.config.CalendarProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyConversionServiceTest {

    private CurrencyConversionService newService() {
        CalendarProperties properties = CalendarProperties.defaults()
                .withExchangeRates(Map.of(
                        "USD", new BigDecimal("0.21"),
                        "THB", new BigDecimal("7.60")));
        return new CurrencyConversionService(new InMemoryExchangeRateProvider(properties));
    }

    @Test
    void convertsBaseCurrencyToItselfUnchanged() {
        BigDecimal result = newService().convertFromBase(new BigDecimal("100.00"), "MYR");
        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void convertsToRequestedCurrencyUsingConfiguredRate() {
        CurrencyConversionService service = newService();
        assertThat(service.convertFromBase(new BigDecimal("100.00"), "USD")).isEqualByComparingTo("21.00");
        assertThat(service.convertFromBase(new BigDecimal("100.00"), "THB")).isEqualByComparingTo("760.00");
    }

    @Test
    void isCaseInsensitiveForCurrencyCodes() {
        assertThat(newService().convertFromBase(new BigDecimal("10"), "usd")).isEqualByComparingTo("2.10");
    }

    @Test
    void returnsNullForNullAmount() {
        assertThat(newService().convertFromBase(null, "USD")).isNull();
    }

    @Test
    void rejectsUnsupportedCurrency() {
        CurrencyConversionService service = newService();
        assertThat(service.isSupported("JPY")).isFalse();
        assertThatThrownBy(() -> service.convertFromBase(new BigDecimal("100"), "JPY"))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }
}
