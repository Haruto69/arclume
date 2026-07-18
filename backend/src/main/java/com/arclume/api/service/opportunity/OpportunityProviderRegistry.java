package com.arclume.api.service.opportunity;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OpportunityProviderRegistry {

    private final Map<String, OpportunityProvider> providersByKey;

    public OpportunityProviderRegistry(List<OpportunityProvider> providers) {
        List<OpportunityProvider> sortedProviders = new ArrayList<>(providers);
        sortedProviders.sort(Comparator.comparing(provider -> normalizeKey(provider.providerKey())));

        Map<String, OpportunityProvider> registered = new LinkedHashMap<>();
        for (OpportunityProvider provider : sortedProviders) {
            String providerKey = normalizeKey(provider.providerKey());
            OpportunityProvider existing = registered.putIfAbsent(providerKey, provider);
            if (existing != null) {
                throw new IllegalStateException("Duplicate opportunity provider key: " + providerKey);
            }
        }
        this.providersByKey = Collections.unmodifiableMap(new LinkedHashMap<>(registered));
    }

    public OpportunityProvider getRequired(String providerKey) {
        String normalizedKey = normalizeKey(providerKey);
        OpportunityProvider provider = providersByKey.get(normalizedKey);
        if (provider == null) {
            throw new UnknownOpportunityProviderException(normalizedKey);
        }
        return provider;
    }

    public List<OpportunityProvider> providers() {
        return providersByKey.values().stream()
                .sorted(Comparator.comparing(provider -> normalizeKey(provider.providerKey())))
                .toList();
    }

    public static String normalizeKey(String providerKey) {
        if (providerKey == null || providerKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider key is required");
        }
        return providerKey.trim().toUpperCase(Locale.ROOT);
    }
}
