package com.airasia.flight.provider;

import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.ProviderProperties.ProviderSettings;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Searcher for AirAsia (Malaysia) — IATA carrier {@code AK} — on its Navitaire
 * New Skies instance. Mirrors the prod {@code NavitaireSearcher} pattern.
 */
@Component
public class AirAsiaMalaysiaSearcher extends AbstractFlightSearcher {

    private final MockNavitaireEngine engine;
    private final ProviderSettings settings;

    public AirAsiaMalaysiaSearcher(MockNavitaireEngine engine, ProviderProperties properties) {
        this.engine = engine;
        this.settings = properties.getAirAsiaMalaysia();
    }

    @Override
    public String providerId() {
        return "airasia-malaysia";
    }

    @Override
    public String displayName() {
        return settings.getDisplayName();
    }

    @Override
    protected List<ProviderFare> doSearch(FlightSearchQuery query) {
        return engine.search(providerId(), settings, query);
    }
}
