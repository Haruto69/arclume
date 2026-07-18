package com.arclume.api.service.opportunity;

import com.arclume.api.domain.OpportunityCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpportunityProviderRegistryTest {

    @Test
    void lookupNormalizesProviderKeys() {
        FakeProvider remotive = new FakeProvider("REMOTIVE", true);
        OpportunityProviderRegistry registry = new OpportunityProviderRegistry(List.of(remotive));

        assertThat(registry.getRequired(" remotive ")).isSameAs(remotive);
    }

    @Test
    void unknownProviderIsRejected() {
        OpportunityProviderRegistry registry = new OpportunityProviderRegistry(List.of(new FakeProvider("REMOTIVE", true)));

        assertThatThrownBy(() -> registry.getRequired("missing"))
                .isInstanceOf(UnknownOpportunityProviderException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    void duplicateProviderKeysFailClearly() {
        assertThatThrownBy(() -> new OpportunityProviderRegistry(List.of(
                new FakeProvider("remotive", true),
                new FakeProvider(" REMOTIVE ", false)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate opportunity provider key: REMOTIVE");
    }

    @Test
    void providersAreReturnedInDeterministicKeyOrder() {
        OpportunityProviderRegistry registry = new OpportunityProviderRegistry(List.of(
                new FakeProvider("Z_TEST", true),
                new FakeProvider("A_TEST", false)
        ));

        assertThat(registry.providers())
                .extracting(OpportunityProvider::providerKey)
                .containsExactly("A_TEST", "Z_TEST");
        assertThat(registry.getRequired("a_test").isEnabled()).isFalse();
    }

    private record FakeProvider(String providerKey, boolean enabled) implements OpportunityProvider {

        @Override
        public OpportunityCategory category() {
            return OpportunityCategory.JOB;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public OpportunityProviderFetchResult fetchOpportunities() {
            return OpportunityProviderFetchResult.empty();
        }
    }
}
