package com.arclume.api.service.opportunity;

public class UnknownOpportunityProviderException extends RuntimeException {

    public UnknownOpportunityProviderException(String providerKey) {
        super("Unsupported opportunity provider: " + providerKey);
    }
}
