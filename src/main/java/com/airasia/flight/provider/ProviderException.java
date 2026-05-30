package com.airasia.flight.provider;

public class ProviderException extends RuntimeException {

    private final String providerId;

    public ProviderException(String providerId, Throwable cause) {
        super("Provider '" + providerId + "' failed: " + cause.getMessage(), cause);
        this.providerId = providerId;
    }

    public String providerId() {
        return providerId;
    }
}
