package com.arclume.api.service.opportunity;

import com.arclume.api.client.RemotiveJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemotiveJobProviderTest {

    private final StubRemotiveJobClient client = new StubRemotiveJobClient();
    private final OpportunityProviderProperties properties = new OpportunityProviderProperties();
    private final RemotiveJobProvider provider = new RemotiveJobProvider(client, properties);

    @Test
    void providerIsDisabledByDefault() {
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void normalizesValidPayloadWithAttributionSanitizationAndMappings() {
        RemotiveJobClient.RemotiveJob job = remotiveJob("remotive-1", "Java Developer", "Arclume Labs");
        job.setUrl("https://remotive.com/remote-jobs/software-dev/java-developer");
        job.setCandidateRequiredLocation("Worldwide");
        job.setJobType("full-time");
        job.setSalary("$100k");
        job.setPublicationDate("2026-07-18T10:15:30Z");
        job.setDescription("<p>Build APIs</p><script>alert('x')</script><img src=x onerror=alert(1)>");
        client.returnJobs(List.of(job));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record).isInstanceOf(NormalizedJobOpportunity.class);
            NormalizedJobOpportunity normalized = (NormalizedJobOpportunity) record;
            assertThat(normalized.sourceId()).isEqualTo("remotive-1");
            assertThat(normalized.sourceUrl()).isEqualTo("https://remotive.com/remote-jobs/software-dev/java-developer");
            assertThat(normalized.attributionLabel()).isEqualTo("Jobs provided by Remotive");
            assertThat(normalized.title()).isEqualTo("Java Developer");
            assertThat(normalized.company()).isEqualTo("Arclume Labs");
            assertThat(normalized.location()).isEqualTo("Worldwide");
            assertThat(normalized.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
            assertThat(normalized.workMode()).isEqualTo(WorkMode.REMOTE);
            assertThat(normalized.salaryRange()).isEqualTo("$100k");
            assertThat(normalized.postedAt()).isNotNull();
            assertThat(normalized.description()).isEqualTo("<p>Build APIs</p>");
            assertThat(normalized.requirements()).isEmpty();
            assertThat(normalized.active()).isTrue();
        });
    }

    @Test
    void skipsMalformedRecordsAndSupportsEmptyPayload() {
        RemotiveJobClient.RemotiveJob missingTitle = remotiveJob("missing-title", " ", "Arclume Labs");
        RemotiveJobClient.RemotiveJob valid = remotiveJob("valid", "Backend Developer", "Arclume Labs");
        client.returnJobs(List.of(missingTitle, valid));

        OpportunityProviderFetchResult first = provider.fetchOpportunities();
        client.returnJobs(List.of());
        OpportunityProviderFetchResult second = provider.fetchOpportunities();

        assertThat(first.recordsFetched()).isEqualTo(2);
        assertThat(first.recordsSkipped()).isEqualTo(1);
        assertThat(first.records()).hasSize(1);
        assertThat(second.recordsFetched()).isZero();
        assertThat(second.recordsSkipped()).isZero();
        assertThat(second.records()).isEmpty();
    }

    @Test
    void clientFailuresPropagateToTheOrchestrator() {
        client.failWith(new OpportunityProviderException("Remotive timeout", null));

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessageContaining("Remotive timeout");
    }

    private RemotiveJobClient.RemotiveJob remotiveJob(String id, String title, String company) {
        RemotiveJobClient.RemotiveJob job = new RemotiveJobClient.RemotiveJob();
        job.setId(id);
        job.setTitle(title);
        job.setCompanyName(company);
        return job;
    }

    private static class StubRemotiveJobClient extends RemotiveJobClient {
        private List<RemotiveJob> jobs = List.of();
        private RuntimeException failure;

        StubRemotiveJobClient() {
            super(RestClient.builder(), new OpportunityProviderProperties());
        }

        void returnJobs(List<RemotiveJob> jobs) {
            this.jobs = jobs;
            this.failure = null;
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public List<RemotiveJob> fetchJobs() {
            if (failure != null) {
                throw failure;
            }
            return jobs;
        }
    }
}
