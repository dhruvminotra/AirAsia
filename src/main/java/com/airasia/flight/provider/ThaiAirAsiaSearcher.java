package com.airasia.flight.provider;

import com.airasia.flight.model.ProviderFare;
import com.airasia.flight.provider.ProviderProperties.ProviderSettings;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Searcher for Thai AirAsia — IATA carrier {@code FD} — on its Navitaire New
 * Skies instance. Mirrors the prod {@code NavitaireSearcher} pattern.
 */
@Component
public class ThaiAirAsiaSearcher extends AbstractFlightSearcher {

    private final MockNavitaireEngine engine;
    private final ProviderSettings settings;

    public ThaiAirAsiaSearcher(MockNavitaireEngine engine, ProviderProperties properties) {
        this.engine = engine;
        this.settings = properties.getThaiAirAsia();
    }

    @Override
    public String providerId() {
        return "thai-airasia";
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
