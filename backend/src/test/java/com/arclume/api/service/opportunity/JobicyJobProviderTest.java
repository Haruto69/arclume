package com.arclume.api.service.opportunity;

import com.arclume.api.client.JobicyJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobicyJobProviderTest {

    private final StubJobicyJobClient client = new StubJobicyJobClient();
    private final OpportunityProviderProperties properties = new OpportunityProviderProperties();
    private final JobicyJobProvider provider = new JobicyJobProvider(client, properties);

    @Test
    void providerMetadataAndDefaultActivationAreStable() {
        assertThat(provider.providerKey()).isEqualTo("JOBICY");
        assertThat(provider.category()).isEqualTo(OpportunityCategory.JOB);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void normalizesValidCurrentPayloadWithAttributionSanitizationAndMappings() {
        JobicyJobClient.JobicyJob job = jobicyJob(12345, "Java Developer", "Arclume Labs");
        job.setUrl("https://jobicy.com/jobs/12345-java-developer");
        job.setJobGeo("Worldwide");
        job.setJobType("full-time");
        job.setSalaryMin(new BigDecimal("50000.00"));
        job.setSalaryMax(new BigDecimal("70000"));
        job.setSalaryCurrency("USD");
        job.setSalaryPeriod("year");
        job.setPubDate("2026-07-18T10:15:30+00:00");
        job.setJobDescription("<p>Build APIs</p><script>alert('x')</script><img src=x onerror=alert(1)>");
        job.setCompanyLogo("https://jobicy.com/logo.png");
        job.setJobExcerpt("Do not map me");
        job.setJobIndustry("Software");
        job.setJobLevel("Senior");
        client.returnJobs(List.of(job));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record).isInstanceOf(NormalizedJobOpportunity.class);
            NormalizedJobOpportunity normalized = (NormalizedJobOpportunity) record;
            assertThat(normalized.sourceId()).isEqualTo("12345");
            assertThat(normalized.sourceUrl()).isEqualTo("https://jobicy.com/jobs/12345-java-developer");
            assertThat(normalized.attributionLabel()).isEqualTo("Jobs provided by Jobicy");
            assertThat(normalized.title()).isEqualTo("Java Developer");
            assertThat(normalized.company()).isEqualTo("Arclume Labs");
            assertThat(normalized.location()).isEqualTo("Worldwide");
            assertThat(normalized.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
            assertThat(normalized.workMode()).isEqualTo(WorkMode.REMOTE);
            assertThat(normalized.salaryRange()).isEqualTo("USD 50000 - 70000 / year");
            assertThat(normalized.postedAt()).isEqualTo(Instant.parse("2026-07-18T10:15:30Z"));
            assertThat(normalized.description()).isEqualTo("<p>Build APIs</p>");
            assertThat(normalized.requirements()).isEmpty();
            assertThat(normalized.active()).isTrue();
        });
    }

    @Test
    void normalizesLegacySalaryFieldsAndPreservesCanonicalUrl() {
        JobicyJobClient.JobicyJob job = jobicyJob(" legacy-id ", "Backend Developer", "Legacy Co");
        job.setUrl(" https://jobicy.com/jobs/legacy-id ");
        job.setSalaryMin(new BigDecimal("40000"));
        job.setSalaryMax(new BigDecimal("60000"));
        job.setSalaryCurrency("EUR");
        job.setSalaryPeriod("year");
        client.returnJobs(List.of(job));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).singleElement().satisfies(record -> {
            NormalizedJobOpportunity normalized = (NormalizedJobOpportunity) record;
            assertThat(normalized.sourceId()).isEqualTo("legacy-id");
            assertThat(normalized.sourceUrl()).isEqualTo("https://jobicy.com/jobs/legacy-id");
            assertThat(normalized.salaryRange()).isEqualTo("EUR 40000 - 60000 / year");
        });
    }

    @Test
    void formatsPartialSalaryBoundsWithoutFabricatingValues() {
        JobicyJobClient.JobicyJob minOnly = jobicyJob("min", "Hourly Developer", "Hourly Co");
        minOnly.setUrl("https://jobicy.com/jobs/min");
        minOnly.setSalaryMin(new BigDecimal("40.00"));
        minOnly.setSalaryCurrency("EUR");
        minOnly.setSalaryPeriod("hour");

        JobicyJobClient.JobicyJob maxOnly = jobicyJob("max", "Manager", "Cap Co");
        maxOnly.setUrl("https://jobicy.com/jobs/max");
        maxOnly.setSalaryMax(new BigDecimal("90000"));
        maxOnly.setSalaryCurrency("USD");
        maxOnly.setSalaryPeriod("year");
        client.returnJobs(List.of(minOnly, maxOnly));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).salaryRange())
                .containsExactly("EUR 40 / hour", "Up to USD 90000 / year");
    }

    @Test
    void mapsSupportedEmploymentTypesAndUnknownTypesToNull() {
        assertEmploymentType("full-time", EmploymentType.FULL_TIME);
        assertEmploymentType("full_time", EmploymentType.FULL_TIME);
        assertEmploymentType("fulltime", EmploymentType.FULL_TIME);
        assertEmploymentType("part-time", EmploymentType.PART_TIME);
        assertEmploymentType("part_time", EmploymentType.PART_TIME);
        assertEmploymentType("parttime", EmploymentType.PART_TIME);
        assertEmploymentType("contract", EmploymentType.CONTRACT);
        assertEmploymentType("internship", EmploymentType.INTERNSHIP);
        assertEmploymentType("intern", EmploymentType.INTERNSHIP);
        assertEmploymentType("volunteer", null);
        assertEmploymentType(null, null);
    }

    @Test
    void invalidPublicationDateMapsToNull() {
        JobicyJobClient.JobicyJob job = jobicyJob("bad-date", "QA Engineer", "QA Co");
        job.setUrl("https://jobicy.com/jobs/bad-date");
        job.setPubDate("not a date");
        client.returnJobs(List.of(job));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(((NormalizedJobOpportunity) record).postedAt()).isNull());
    }

    @Test
    void skipsRecordsMissingRequiredFieldsAndCountsFetchedAndSkipped() {
        JobicyJobClient.JobicyJob missingId = jobicyJob(" ", "Developer", "Co");
        missingId.setUrl("https://jobicy.com/jobs/missing-id");
        JobicyJobClient.JobicyJob missingUrl = jobicyJob("missing-url", "Developer", "Co");
        JobicyJobClient.JobicyJob missingTitle = jobicyJob("missing-title", " ", "Co");
        missingTitle.setUrl("https://jobicy.com/jobs/missing-title");
        JobicyJobClient.JobicyJob missingCompany = jobicyJob("missing-company", "Developer", " ");
        missingCompany.setUrl("https://jobicy.com/jobs/missing-company");
        JobicyJobClient.JobicyJob valid = jobicyJob("valid", "Developer", "Co");
        valid.setUrl("https://jobicy.com/jobs/valid");
        client.returnJobs(List.of(missingId, missingUrl, missingTitle, missingCompany, valid));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(5);
        assertThat(result.recordsSkipped()).isEqualTo(4);
        assertThat(result.records()).hasSize(1);
    }

    @Test
    void supportsEmptyPayloadWithoutAttemptingDeactivation() {
        client.returnJobs(List.of());

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isZero();
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    void clientFailuresPropagateToTheOrchestrator() {
        client.failWith(new OpportunityProviderException("Jobicy timeout", null));

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessageContaining("Jobicy timeout");
    }

    private void assertEmploymentType(String jobType, EmploymentType expected) {
        JobicyJobClient.JobicyJob job = jobicyJob("type-" + String.valueOf(jobType), "Developer", "Type Co");
        job.setUrl("https://jobicy.com/jobs/type-" + String.valueOf(jobType));
        job.setJobType(jobType);
        client.returnJobs(List.of(job));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(((NormalizedJobOpportunity) record).employmentType()).isEqualTo(expected));
    }

    private JobicyJobClient.JobicyJob jobicyJob(Object id, String title, String company) {
        JobicyJobClient.JobicyJob job = new JobicyJobClient.JobicyJob();
        job.setId(id);
        job.setJobTitle(title);
        job.setCompanyName(company);
        return job;
    }

    private static class StubJobicyJobClient extends JobicyJobClient {
        private List<JobicyJob> jobs = List.of();
        private RuntimeException failure;

        StubJobicyJobClient() {
            super(RestClient.builder(), new OpportunityProviderProperties());
        }

        void returnJobs(List<JobicyJob> jobs) {
            this.jobs = jobs;
            this.failure = null;
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public List<JobicyJob> fetchJobs() {
            if (failure != null) {
                throw failure;
            }
            return jobs;
        }
    }
}
