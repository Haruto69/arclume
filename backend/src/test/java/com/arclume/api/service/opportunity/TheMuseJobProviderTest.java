package com.arclume.api.service.opportunity;

import com.arclume.api.client.TheMuseJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TheMuseJobProviderTest {

    private static final String API_KEY = "test-muse-key";

    private StubTheMuseJobClient client;
    private OpportunityProviderProperties properties;
    private TheMuseJobProvider provider;

    @BeforeEach
    void setUp() {
        client = new StubTheMuseJobClient();
        properties = new OpportunityProviderProperties();
        provider = new TheMuseJobProvider(client, properties);
    }

    @Test
    void providerMetadataDefaultActivationAndCredentialOnlyBehaviorAreStable() {
        assertThat(provider.providerKey()).isEqualTo("THE_MUSE");
        assertThat(provider.category()).isEqualTo(OpportunityCategory.JOB);
        assertThat(TheMuseJobProvider.ATTRIBUTION_LABEL).isEqualTo("Jobs provided by The Muse");
        assertThat(provider.isEnabled()).isFalse();

        properties.getProviders().getTheMuse().setApiKey(API_KEY);
        assertThat(provider.isEnabled()).isFalse();

        properties.getProviders().getTheMuse().setEnabled(true);
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void enabledProviderRequiresRegisteredApiKeyBeforeHttp() {
        properties.getProviders().getTheMuse().setEnabled(true);

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("The Muse provider requires a registered application API key")
                .hasMessageNotContaining("api_key");
        assertThat(client.requestedPages()).isEmpty();
    }

    @Test
    void fetchesSequentialPagesFromZeroUntilFinalEmptyOrMaxPages() {
        enableProvider();
        client.returnPage(0, page(0, 3, List.of(job(1L, "One", "Muse Co", "https://www.themuse.com/jobs/muse/one"))));
        client.returnPage(1, page(1, 3, List.of(job(2L, "Two", "Muse Co", "https://www.themuse.com/jobs/muse/two"))));
        client.returnPage(2, page(2, 3, List.of(job(3L, "Three", "Muse Co", "https://www.themuse.com/jobs/muse/three"))));

        OpportunityProviderFetchResult finalPage = provider.fetchOpportunities();

        assertThat(finalPage.recordsFetched()).isEqualTo(3);
        assertThat(finalPage.recordsSkipped()).isZero();
        assertThat(finalPage.recordsFailed()).isZero();
        assertThat(client.requestedPages()).containsExactly(0, 1, 2);
        assertThat(client.requestedKeys()).containsExactly(API_KEY, API_KEY, API_KEY);

        setUp();
        enableProvider();
        client.returnPage(0, page(0, 5, List.of()));

        OpportunityProviderFetchResult emptyPage = provider.fetchOpportunities();

        assertThat(emptyPage.recordsFetched()).isZero();
        assertThat(emptyPage.records()).isEmpty();
        assertThat(client.requestedPages()).containsExactly(0);

        setUp();
        enableProvider();
        properties.getProviders().getTheMuse().setMaxPages(2);
        client.returnPage(0, page(0, 10, List.of(job(10L, "Ten", "Muse Co", "https://www.themuse.com/jobs/muse/ten"))));
        client.returnPage(1, page(1, 10, List.of(job(11L, "Eleven", "Muse Co", "https://www.themuse.com/jobs/muse/eleven"))));

        OpportunityProviderFetchResult capped = provider.fetchOpportunities();

        assertThat(capped.recordsFetched()).isEqualTo(2);
        assertThat(client.requestedPages()).containsExactly(0, 1);
    }

    @Test
    void pageFailurePropagatesAsProviderWideFailureWithoutPartialSuccess() {
        enableProvider();
        client.returnPage(0, page(0, 2, List.of(job(1L, "One", "Muse Co", "https://www.themuse.com/jobs/muse/one"))));
        client.failPage(1);

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage(TheMuseJobClient.FAILURE_MESSAGE);
        assertThat(client.requestedPages()).containsExactly(0, 1);
    }

    @Test
    void normalizesValidJobWithCanonicalCurrentUrlAttributionSanitizedContentAndNullSalaryRequirements() {
        enableProvider();
        TheMuseJobClient.TheMuseJob job = job(12345L, "Backend Engineer", "Arclume Labs",
                "https://www.themuse.com/jobs/arclume/backend-engineer?ref=api#ignored");
        job.setShortName("backend-engineer");
        job.setType("Full Time");
        job.setPublicationDate("2026-07-18T10:15:30+00:00");
        job.setContents("<h2 onclick=\"x\">Role</h2><p>Build <strong>services</strong></p>"
                + "<script>alert(1)</script><a href=\"https://example.com\" onclick=\"x\">safe</a>"
                + "<img src=\"https://example.com/logo.png\">");
        job.setLocations(List.of(named(" Remote "), named("Remote"), named("Remote, United States")));
        job.setLevels(List.of(named("Internship"), named("Senior")));
        job.setCategories(List.of(named("Engineering")));
        client.returnPage(0, page(0, 1, List.of(job)));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement().satisfies(record -> {
            NormalizedJobOpportunity normalized = (NormalizedJobOpportunity) record;
            assertThat(normalized.sourceId()).isEqualTo("12345");
            assertThat(normalized.sourceUrl()).isEqualTo("https://www.themuse.com/jobs/arclume/backend-engineer?ref=api");
            assertThat(normalized.attributionLabel()).isEqualTo("Jobs provided by The Muse");
            assertThat(normalized.title()).isEqualTo("Backend Engineer");
            assertThat(normalized.company()).isEqualTo("Arclume Labs");
            assertThat(normalized.location()).isEqualTo("Remote, Remote, United States");
            assertThat(normalized.workMode()).isEqualTo(WorkMode.REMOTE);
            assertThat(normalized.employmentType()).isEqualTo(EmploymentType.INTERNSHIP);
            assertThat(normalized.salaryRange()).isNull();
            assertThat(normalized.postedAt()).isEqualTo(Instant.parse("2026-07-18T10:15:30Z"));
            assertThat(normalized.description()).contains("<h2>Role</h2>", "<strong>services</strong>", "href=\"https://example.com\"");
            assertThat(normalized.description()).doesNotContain("script", "onclick", "<img", "Engineering", "Senior");
            assertThat(normalized.requirements()).isNull();
            assertThat(normalized.active()).isTrue();
        });
    }

    @Test
    void normalizesLegacyHostAndRejectsUnsafeOrNonMuseUrls() {
        enableProvider();
        client.returnPage(0, page(0, 1, List.of(
                job(1L, "Legacy", "Muse Co", "https://api-v2.themuse.com/jobs/muse/legacy?utm=api#fragment"),
                job(2L, "Root Host", "Muse Co", "https://themuse.com/jobs/muse/root"),
                job(3L, "Bad Host", "Muse Co", "https://evil.example/jobs/muse/bad"),
                job(4L, "Http", "Muse Co", "http://www.themuse.com/jobs/muse/http"),
                job(5L, "Credentials", "Muse Co", "https://user:pass@www.themuse.com/jobs/muse/creds"),
                job(6L, "Blank Path", "Muse Co", "https://www.themuse.com/"),
                job(7L, "Scheme Relative", "Muse Co", "//www.themuse.com/jobs/muse/scheme")
        )));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(7);
        assertThat(result.recordsSkipped()).isEqualTo(5);
        assertThat(result.records())
                .extracting(NormalizedOpportunityRecord::sourceUrl)
                .containsExactly(
                        "https://www.themuse.com/jobs/muse/legacy?utm=api",
                        "https://www.themuse.com/jobs/muse/root");
    }

    @Test
    void canonicalMusePathValidationRejectsMalformedPathsAndNormalizesAcceptedHosts() {
        enableProvider();
        client.returnPage(0, page(0, 1, List.of(
                job(10L, "Current Nested", "Muse Co", "https://www.themuse.com/jobs/company/job-slug"),
                job(11L, "Root Nested", "Muse Co", "https://themuse.com/jobs/company/job-slug"),
                job(12L, "Legacy Nested", "Muse Co", "https://api-v2.themuse.com/jobs/company/job-slug?ref=api#fragment"),
                job(13L, "Only Slashes", "Muse Co", "https://www.themuse.com/jobs//"),
                job(14L, "Parent", "Muse Co", "https://www.themuse.com/jobs/../account"),
                job(15L, "Encoded Parent", "Muse Co", "https://www.themuse.com/jobs/%2e%2e/account"),
                job(16L, "Escaping Nested", "Muse Co", "https://www.themuse.com/jobs/company/../../account"),
                job(17L, "Backslash", "Muse Co", "https://www.themuse.com/jobs/\\account")
        )));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(8);
        assertThat(result.recordsSkipped()).isEqualTo(5);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records())
                .extracting(NormalizedOpportunityRecord::sourceUrl)
                .containsExactly(
                        "https://www.themuse.com/jobs/company/job-slug",
                        "https://www.themuse.com/jobs/company/job-slug",
                        "https://www.themuse.com/jobs/company/job-slug?ref=api");
    }
    @Test
    void locationAggregationPreservesOrderDeduplicatesExactTrimmedValuesAndHonorsBounds() {
        enableProvider();
        TheMuseJobClient.TheMuseJob bounded = job(1L, "Bounded", "Muse Co", "https://www.themuse.com/jobs/muse/bounded");
        bounded.setLocations(List.of(
                named("  Berlin  "),
                named("Berlin"),
                named("Paris"),
                named("x".repeat(201)),
                named("A".repeat(190)),
                named("Too Far")
        ));
        TheMuseJobClient.TheMuseJob physical = job(2L, "Physical", "Muse Co", "https://www.themuse.com/jobs/muse/physical");
        physical.setLocations(List.of(named("New York")));
        TheMuseJobClient.TheMuseJob mixed = job(3L, "Mixed", "Muse Co", "https://www.themuse.com/jobs/muse/mixed");
        mixed.setLocations(List.of(named("Remote"), named("London")));
        TheMuseJobClient.TheMuseJob missing = job(4L, "Missing", "Muse Co", "https://www.themuse.com/jobs/muse/missing");
        client.returnPage(0, page(0, 1, List.of(bounded, physical, mixed, missing)));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).location())
                .containsExactly("Berlin, Paris, Too Far", "New York", "Remote, London", null);
        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).workMode())
                .containsExactly(null, null, null, null);
    }

    @Test
    void employmentTypeUsesOnlyExactInternshipLevelAndIgnoresApiType() {
        enableProvider();
        TheMuseJobClient.TheMuseJob internship = jobWithLevel(1L, "Internship", "Full Time");
        TheMuseJobClient.TheMuseJob entry = jobWithLevel(2L, "Entry Level", "Internship");
        TheMuseJobClient.TheMuseJob senior = jobWithLevel(3L, "Senior Level", "Full Time");
        client.returnPage(0, page(0, 1, List.of(internship, entry, senior)));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).employmentType())
                .containsExactly(EmploymentType.INTERNSHIP, null, null);
    }

    @Test
    void blankContentsMapToNullAndUnsafeLinksLoseHref() {
        enableProvider();
        TheMuseJobClient.TheMuseJob blank = job(1L, "Blank", "Muse Co", "https://www.themuse.com/jobs/muse/blank");
        blank.setContents("   ");
        TheMuseJobClient.TheMuseJob unsafe = job(2L, "Unsafe", "Muse Co", "https://www.themuse.com/jobs/muse/unsafe");
        unsafe.setContents("<p><a href=\"javascript:alert(1)\" onclick=\"x\">bad link</a></p><form><input></form><iframe></iframe>");
        client.returnPage(0, page(0, 1, List.of(blank, unsafe)));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).description())
                .containsExactly(null, "<p><a>bad link</a></p>");
    }

    @Test
    void skipsMissingInvalidRequiredFieldsMalformedDatesDuplicatesAndCountsRawRecords() {
        enableProvider();
        TheMuseJobClient.TheMuseJob missingId = job(null, "Developer", "Muse Co", "https://www.themuse.com/jobs/muse/missing-id");
        TheMuseJobClient.TheMuseJob nonpositiveId = job(0L, "Developer", "Muse Co", "https://www.themuse.com/jobs/muse/nonpositive");
        TheMuseJobClient.TheMuseJob blankTitle = job(3L, " ", "Muse Co", "https://www.themuse.com/jobs/muse/blank-title");
        TheMuseJobClient.TheMuseJob overlongTitle = job(4L, "T".repeat(201), "Muse Co", "https://www.themuse.com/jobs/muse/over-title");
        TheMuseJobClient.TheMuseJob blankCompany = job(5L, "Developer", " ", "https://www.themuse.com/jobs/muse/blank-company");
        TheMuseJobClient.TheMuseJob overlongCompany = job(6L, "Developer", "C".repeat(201), "https://www.themuse.com/jobs/muse/over-company");
        TheMuseJobClient.TheMuseJob badDate = job(7L, "Developer", "Muse Co", "https://www.themuse.com/jobs/muse/bad-date");
        badDate.setPublicationDate("not a date");
        TheMuseJobClient.TheMuseJob valid = job(8L, "Developer", "Muse Co", "https://www.themuse.com/jobs/muse/valid");
        TheMuseJobClient.TheMuseJob duplicate = job(8L, "Duplicate", "Muse Co", "https://www.themuse.com/jobs/muse/duplicate");
        client.returnPage(0, page(0, 1, List.of(
                missingId,
                nonpositiveId,
                blankTitle,
                overlongTitle,
                blankCompany,
                overlongCompany,
                badDate,
                valid,
                duplicate
        )));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(9);
        assertThat(result.recordsSkipped()).isEqualTo(8);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(record.sourceId()).isEqualTo("8"));
    }

    @Test
    void duplicateSourceIdKeepsFirstOccurrenceEvenWhenFirstRecordIsInvalid() {
        enableProvider();
        TheMuseJobClient.TheMuseJob invalidFirst = job(99L, "Invalid First", "Muse Co", "https://evil.example/jobs/muse/invalid");
        TheMuseJobClient.TheMuseJob validDuplicate = job(99L, "Valid Duplicate", "Muse Co", "https://www.themuse.com/jobs/muse/valid-duplicate");
        client.returnPage(0, page(0, 1, List.of(invalidFirst, validDuplicate)));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(2);
        assertThat(result.recordsSkipped()).isEqualTo(2);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).isEmpty();
    }
    @Test
    void clientFailurePropagatesWithoutMutationOrAiEnrichment() {
        enableProvider();
        client.failPage(0);

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage(TheMuseJobClient.FAILURE_MESSAGE);
    }

    private void enableProvider() {
        properties.getProviders().getTheMuse().setEnabled(true);
        properties.getProviders().getTheMuse().setApiKey(API_KEY);
    }

    private TheMuseJobClient.TheMuseJob jobWithLevel(Long id, String level, String type) {
        TheMuseJobClient.TheMuseJob job = job(id, "Developer " + id, "Muse Co", "https://www.themuse.com/jobs/muse/" + id);
        job.setLevels(List.of(named(level)));
        job.setType(type);
        return job;
    }

    private TheMuseJobClient.TheMuseJob job(Long id, String title, String companyName, String landingPage) {
        TheMuseJobClient.TheMuseJob job = new TheMuseJobClient.TheMuseJob();
        job.setId(id);
        job.setName(title);
        TheMuseJobClient.TheMuseCompany company = new TheMuseJobClient.TheMuseCompany();
        company.setId(1L);
        company.setName(companyName);
        company.setShortName("muse-co");
        job.setCompany(company);
        TheMuseJobClient.TheMuseRefs refs = new TheMuseJobClient.TheMuseRefs();
        refs.setLandingPage(landingPage);
        job.setRefs(refs);
        return job;
    }

    private TheMuseJobClient.TheMuseNamedValue named(String name) {
        TheMuseJobClient.TheMuseNamedValue value = new TheMuseJobClient.TheMuseNamedValue();
        value.setName(name);
        return value;
    }

    private TheMuseJobClient.TheMuseJobPage page(
            int page,
            int pageCount,
            List<TheMuseJobClient.TheMuseJob> jobs) {
        return new TheMuseJobClient.TheMuseJobPage(page, pageCount, jobs);
    }

    private static class StubTheMuseJobClient extends TheMuseJobClient {
        private final Map<Integer, TheMuseJobPage> pages = new HashMap<>();
        private final List<Integer> requestedPages = new ArrayList<>();
        private final List<String> requestedKeys = new ArrayList<>();
        private final List<Integer> failingPages = new ArrayList<>();

        StubTheMuseJobClient() {
            super(RestClient.builder(), new OpportunityProviderProperties());
        }

        void returnPage(int page, TheMuseJobPage response) {
            pages.put(page, response);
        }

        void failPage(int page) {
            failingPages.add(page);
        }

        List<Integer> requestedPages() {
            return requestedPages;
        }

        List<String> requestedKeys() {
            return requestedKeys;
        }

        @Override
        public TheMuseJobPage fetchPage(int page, String apiKey) {
            requestedPages.add(page);
            requestedKeys.add(apiKey);
            if (failingPages.contains(page)) {
                throw new OpportunityProviderException(TheMuseJobClient.FAILURE_MESSAGE, null);
            }
            return pages.getOrDefault(page, new TheMuseJobPage(page, 0, List.of()));
        }
    }
}
