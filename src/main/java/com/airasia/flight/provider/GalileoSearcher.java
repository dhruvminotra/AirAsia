package com.airasia.flight.provider;

import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.ProviderProperties.ProviderSettings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Component
public class GalileoSearcher extends AbstractFlightSearcher {

    private static final String CARRIER = "AK";
    private static final int FLIGHTS_PER_DAY = 3;
    private static final String[] PRICE_CLASSES = {"PROMO", "SAVER", "FLEX"};
    private static final double[] CLASS_MULTIPLIER = {1.00, 1.35, 1.80};
    private static final double GALILEO_MARKUP = 1.05;

    private final ProviderSettings settings;

    public GalileoSearcher(ProviderProperties properties) {
        this.settings = properties.settingsFor(providerId());
    }

    @Override
    public String providerId() {
        return "galileo";
    }

    @Override
    public String displayName() {
        return "Galileo";
    }

    @Override
    protected List<ProviderFare> doSearch(FlightSearchQuery query) {
        simulateLatency(settings);
        simulateFailure(settings);

        List<ProviderFare> fares = new ArrayList<>();
        for (LocalDate date : query.dates()) {
            for (int idx = 0; idx < FLIGHTS_PER_DAY; idx++) {
                String flightNumber = CARRIER + (100 + idx);
                BigDecimal base = basePrice(query, date, flightNumber);
                for (int c = 0; c < PRICE_CLASSES.length; c++) {
                    BigDecimal amount = base
                            .multiply(BigDecimal.valueOf(CLASS_MULTIPLIER[c]))
                            .multiply(BigDecimal.valueOf(GALILEO_MARKUP))
                            .setScale(2, RoundingMode.HALF_UP);
                    fares.add(new ProviderFare(providerId(), query.origin(), query.destination(),
                            date, flightNumber, PRICE_CLASSES[c], amount));
                }
            }
        }
        return fares;
    }

    private BigDecimal basePrice(FlightSearchQuery query, LocalDate date, String flightNumber) {
        Random rng = new Random(Objects.hash(query.origin(), query.destination(), date, flightNumber));
        return BigDecimal.valueOf(60 + rng.nextInt(180));
    }
}
