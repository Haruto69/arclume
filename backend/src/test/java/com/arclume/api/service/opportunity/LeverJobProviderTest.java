package com.arclume.api.service.opportunity;

import com.arclume.api.client.LeverJobClient;
import com.arclume.api.config.OpportunityProviderProperties;
import com.arclume.api.config.OpportunityProviderProperties.LeverRegion;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.OpportunityCategory;
import com.arclume.api.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeverJobProviderTest {

    private final StubLeverJobClient client = new StubLeverJobClient();
    private final OpportunityProviderProperties properties = new OpportunityProviderProperties();
    private final LeverJobProvider provider = new LeverJobProvider(client, properties);

    @Test
    void providerMetadataAndDefaultActivationAreStable() {
        assertThat(provider.providerKey()).isEqualTo("LEVER");
        assertThat(provider.category()).isEqualTo(OpportunityCategory.JOB);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void noEnabledSourcesCauseSafeProviderFailureWithoutHttp() {
        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Lever provider requires at least one enabled source");
        assertThat(client.fetchedSources()).isEmpty();
    }

    @Test
    void disabledSourceDoesNotCallClient() {
        properties.getProviders().getLever().setSources(List.of(source(false, "disabled-site", "Disabled Co", LeverRegion.GLOBAL)));

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Lever provider requires at least one enabled source");
        assertThat(client.fetchedSources()).isEmpty();
    }

    @Test
    void normalizesValidPostingWithCanonicalHostedUrlMappingsSalaryAndSanitizedDescription() {
        properties.getProviders().getLever().setSources(List.of(source(true, "Approved_Site", "Approved Company", LeverRegion.GLOBAL)));
        LeverJobClient.LeverPosting posting = posting("posting-1", "Backend Engineer", "https://jobs.lever.co/fake/posting-1");
        posting.setApplyUrl("https://jobs.lever.co/fake/posting-1/apply");
        posting.setCategories(categories("Remote", "full-time", List.of("Remote", "Remote", "London")));
        posting.setWorkplaceType("remote");
        posting.setSalaryRange(salaryRange("USD", "year", "50000.00", "70000"));
        posting.setDescription("<p>Build services</p><script>alert(1)</script>");
        LeverJobClient.LeverListSection section = listSection("Responsibilities", "<p><a href=\"javascript:alert(1)\" onclick=\"x\">bad link</a></p>");
        posting.setLists(List.of(section));
        posting.setAdditional("<p>Additional context</p>");
        client.returnJobs("Approved_Site", LeverRegion.GLOBAL, List.of(posting));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record).isInstanceOf(NormalizedJobOpportunity.class);
            NormalizedJobOpportunity normalized = (NormalizedJobOpportunity) record;
            assertThat(normalized.sourceId()).isEqualTo("global:approved_site:posting-1");
            assertThat(normalized.sourceUrl()).isEqualTo("https://jobs.lever.co/fake/posting-1");
            assertThat(normalized.attributionLabel()).isEqualTo("Job listing via Lever");
            assertThat(normalized.title()).isEqualTo("Backend Engineer");
            assertThat(normalized.company()).isEqualTo("Approved Company");
            assertThat(normalized.location()).isEqualTo("Remote, London");
            assertThat(normalized.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
            assertThat(normalized.workMode()).isEqualTo(WorkMode.REMOTE);
            assertThat(normalized.salaryRange()).isEqualTo("USD 50000 - 70000 / year");
            assertThat(normalized.postedAt()).isNull();
            assertThat(normalized.description()).contains("Build services", "Responsibilities", "Additional context");
            assertThat(normalized.description()).doesNotContain("script", "onclick", "javascript:");
            assertThat(normalized.requirements()).isEmpty();
            assertThat(normalized.active()).isTrue();
        });
    }

    @Test
    void euRegionUsesDistinctCompositeIdentity() {
        properties.getProviders().getLever().setSources(List.of(source(true, "EU_Site", "EU Company", LeverRegion.EU)));
        client.returnJobs("EU_Site", LeverRegion.EU,
                List.of(posting("posting-1", "EU Engineer", "https://jobs.lever.co/fake/posting-1")));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(((NormalizedJobOpportunity) record).sourceId())
                        .isEqualTo("eu:eu_site:posting-1"));
    }

    @Test
    void locationPrefersAllLocationsThenCategoryLocationThenCountry() {
        properties.getProviders().getLever().setSources(List.of(source(true, "location-site", "Location Company", LeverRegion.GLOBAL)));
        LeverJobClient.LeverPosting allLocations = posting("all", "All Locations", "https://jobs.lever.co/fake/all");
        allLocations.setCategories(categories("Fallback", "full-time", List.of("Berlin", "Berlin", "Paris")));
        allLocations.setCountry("DE");
        LeverJobClient.LeverPosting categoryLocation = posting("category", "Category Location", "https://jobs.lever.co/fake/category");
        categoryLocation.setCategories(categories("London", "full-time", List.of(" ")));
        categoryLocation.setCountry("GB");
        LeverJobClient.LeverPosting country = posting("country", "Country", "https://jobs.lever.co/fake/country");
        country.setCountry("US");
        client.returnJobs("location-site", LeverRegion.GLOBAL, List.of(allLocations, categoryLocation, country));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).location())
                .containsExactly("Berlin, Paris", "London", "US");
    }

    @Test
    void mapsSupportedEmploymentTypesAndUnknownTypesToNull() {
        properties.getProviders().getLever().setSources(List.of(source(true, "employment-site", "Employment Company", LeverRegion.GLOBAL)));
        List<LeverJobClient.LeverPosting> postings = List.of(
                postingWithCommitment("full-time", "full-time"),
                postingWithCommitment("full_time", "full_time"),
                postingWithCommitment("full time", "full time"),
                postingWithCommitment("fulltime", "fulltime"),
                postingWithCommitment("part-time", "part-time"),
                postingWithCommitment("contractor", "contractor"),
                postingWithCommitment("internship", "internship"),
                postingWithCommitment("temporary", "temporary")
        );
        client.returnJobs("employment-site", LeverRegion.GLOBAL, postings);

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).employmentType())
                .containsExactly(
                        EmploymentType.FULL_TIME,
                        EmploymentType.FULL_TIME,
                        EmploymentType.FULL_TIME,
                        EmploymentType.FULL_TIME,
                        EmploymentType.PART_TIME,
                        EmploymentType.CONTRACT,
                        EmploymentType.INTERNSHIP,
                        null);
    }

    @Test
    void mapsSupportedWorkModesAndUnknownTypesToNull() {
        properties.getProviders().getLever().setSources(List.of(source(true, "work-site", "Work Company", LeverRegion.GLOBAL)));
        List<LeverJobClient.LeverPosting> postings = List.of(
                postingWithWorkplaceType("remote", "remote"),
                postingWithWorkplaceType("hybrid", "hybrid"),
                postingWithWorkplaceType("on-site", "on-site"),
                postingWithWorkplaceType("onsite", "onsite"),
                postingWithWorkplaceType("on_site", "on_site"),
                postingWithWorkplaceType("unspecified", "unspecified"),
                postingWithWorkplaceType("field", "field")
        );
        client.returnJobs("work-site", LeverRegion.GLOBAL, postings);

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).workMode())
                .containsExactly(
                        WorkMode.REMOTE,
                        WorkMode.HYBRID,
                        WorkMode.ON_SITE,
                        WorkMode.ON_SITE,
                        WorkMode.ON_SITE,
                        null,
                        null);
    }

    @Test
    void formatsSalaryBoundsAndPlainTextFallbackWithoutInventingValues() {
        properties.getProviders().getLever().setSources(List.of(source(true, "salary-site", "Salary Company", LeverRegion.GLOBAL)));
        LeverJobClient.LeverPosting equal = posting("equal", "Equal", "https://jobs.lever.co/fake/equal");
        equal.setSalaryRange(salaryRange("USD", null, "50000.00", "50000"));
        LeverJobClient.LeverPosting minOnly = posting("min", "Min", "https://jobs.lever.co/fake/min");
        minOnly.setSalaryRange(salaryRange("EUR", "hourly", "40.00", null));
        LeverJobClient.LeverPosting maxOnly = posting("max", "Max", "https://jobs.lever.co/fake/max");
        maxOnly.setSalaryRange(salaryRange("GBP", "per year", null, "90000"));
        LeverJobClient.LeverPosting textFallback = posting("text", "Text", "https://jobs.lever.co/fake/text");
        textFallback.setSalaryDescriptionPlain("  Salary   available after interview  ");
        client.returnJobs("salary-site", LeverRegion.GLOBAL, List.of(equal, minOnly, maxOnly, textFallback));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records())
                .extracting(record -> ((NormalizedJobOpportunity) record).salaryRange())
                .containsExactly(
                        "USD 50000",
                        "From EUR 40 / hour",
                        "Up to GBP 90000 / year",
                        "Salary available after interview");
    }

    @Test
    void descriptionAssemblyDoesNotAppendOpeningOrBodyWhenDescriptionExists() {
        properties.getProviders().getLever().setSources(List.of(source(true, "description-site", "Description Company", LeverRegion.GLOBAL)));
        LeverJobClient.LeverPosting posting = posting("description", "Description", "https://jobs.lever.co/fake/description");
        posting.setDescription("<p>Main description</p>");
        posting.setOpening("<p>Opening should not appear</p>");
        posting.setDescriptionBody("<p>Body should not appear</p>");
        posting.setLists(List.of(listSection("Extra", "<p>Ship software</p>")));
        client.returnJobs("description-site", LeverRegion.GLOBAL, List.of(posting));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).singleElement().satisfies(record -> {
            String description = ((NormalizedJobOpportunity) record).description();
            assertThat(description).contains("Main description", "Ship software");
            assertThat(description).doesNotContain("Opening should not appear", "Body should not appear");
        });
    }

    @Test
    void combinedPlainDescriptionPrecedenceDoesNotAppendSeparatePlainFieldsAgain() {
        properties.getProviders().getLever().setSources(List.of(source(true, "plain-description-site", "Description Company", LeverRegion.GLOBAL)));
        LeverJobClient.LeverPosting posting = posting("plain-description", "Plain Description", "https://jobs.lever.co/fake/plain-description");
        posting.setDescriptionPlain("Opening overview Body responsibilities");
        posting.setOpeningPlain("Opening overview");
        posting.setDescriptionBodyPlain("Body responsibilities");
        client.returnJobs("plain-description-site", LeverRegion.GLOBAL, List.of(posting));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).singleElement().satisfies(record -> {
            String description = ((NormalizedJobOpportunity) record).description();
            assertThat(description).contains("Opening overview Body responsibilities");
            assertThat(countOccurrences(description, "Opening overview")).isEqualTo(1);
            assertThat(countOccurrences(description, "Body responsibilities")).isEqualTo(1);
        });
    }

    @Test
    void separatePlainFieldsAreUsedWhenCombinedPlainDescriptionIsAbsent() {
        properties.getProviders().getLever().setSources(List.of(source(true, "plain-fields-site", "Plain Fields Company", LeverRegion.GLOBAL)));
        LeverJobClient.LeverPosting posting = posting("plain-fields", "Plain Fields", "https://jobs.lever.co/fake/plain-fields");
        posting.setDescriptionPlain(" ");
        posting.setOpeningPlain("Opening with <script>alert(1)</script>");
        posting.setDescriptionBodyPlain("Body with <img src=x onerror=alert(1)>");
        client.returnJobs("plain-fields-site", LeverRegion.GLOBAL, List.of(posting));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.records()).singleElement().satisfies(record -> {
            String description = ((NormalizedJobOpportunity) record).description();
            assertThat(countOccurrences(description, "Opening with")).isEqualTo(1);
            assertThat(countOccurrences(description, "Body with")).isEqualTo(1);
            assertThat(description.indexOf("Opening with")).isLessThan(description.indexOf("Body with"));
            assertThat(description).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
            assertThat(description).contains("&lt;img src=x onerror=alert(1)&gt;");
            assertThat(description).doesNotContain("<script", "<img", "onclick");
        });
    }

    @Test
    void missingRequiredFieldsAndMissingHostedUrlWithApplyUrlAreSkipped() {
        properties.getProviders().getLever().setSources(List.of(source(true, "required-site", "Required Company", LeverRegion.GLOBAL)));
        LeverJobClient.LeverPosting missingId = posting(" ", "Developer", "https://jobs.lever.co/fake/missing-id");
        LeverJobClient.LeverPosting missingHostedUrl = posting("missing-hosted", "Developer", " ");
        missingHostedUrl.setApplyUrl("https://jobs.lever.co/fake/missing-hosted/apply");
        LeverJobClient.LeverPosting missingTitle = posting("missing-title", " ", "https://jobs.lever.co/fake/missing-title");
        LeverJobClient.LeverPosting valid = posting("valid", "Developer", "https://jobs.lever.co/fake/valid");
        LeverJobClient.LeverPosting duplicate = posting("valid", "Duplicate Developer", "https://jobs.lever.co/fake/valid-duplicate");
        client.returnJobs("required-site", LeverRegion.GLOBAL,
                List.of(missingId, missingHostedUrl, missingTitle, valid, duplicate));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(5);
        assertThat(result.recordsSkipped()).isEqualTo(4);
        assertThat(result.recordsFailed()).isZero();
        assertThat(result.records()).singleElement()
                .satisfies(record -> assertThat(((NormalizedJobOpportunity) record).sourceId())
                        .isEqualTo("global:required-site:valid"));
    }

    @Test
    void invalidSourceConfigurationCountsAsSourceFailureWhenAnotherSiteSucceeds() {
        properties.getProviders().getLever().setSources(List.of(
                source(true, "bad site", "Bad Source Co", LeverRegion.GLOBAL),
                source(true, "valid-site", "Valid Source Co", LeverRegion.GLOBAL)
        ));
        client.returnJobs("valid-site", LeverRegion.GLOBAL,
                List.of(posting("lever-1", "Developer", "https://jobs.lever.co/fake/lever-1")));

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(client.fetchedSources()).containsExactly("GLOBAL:valid-site");
    }

    @Test
    void oneSuccessfulSitePlusOneFailedSiteReturnsPartialFetchCounters() {
        properties.getProviders().getLever().setSources(List.of(
                source(true, "good-site", "Good Company", LeverRegion.GLOBAL),
                source(true, "failing-site", "Failing Company", LeverRegion.EU)
        ));
        client.returnJobs("good-site", LeverRegion.GLOBAL,
                List.of(posting("lever-1", "Developer", "https://jobs.lever.co/fake/lever-1")));
        client.failSource("failing-site", LeverRegion.EU);

        OpportunityProviderFetchResult result = provider.fetchOpportunities();

        assertThat(result.recordsFetched()).isEqualTo(1);
        assertThat(result.recordsSkipped()).isZero();
        assertThat(result.recordsFailed()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(client.fetchedSources()).containsExactly("GLOBAL:good-site", "EU:failing-site");
    }

    @Test
    void everyEnabledSiteFailingProducesProviderWideSafeException() {
        properties.getProviders().getLever().setSources(List.of(source(true, "failing-site", "Failing Company", LeverRegion.GLOBAL)));
        client.failSource("failing-site", LeverRegion.GLOBAL);

        assertThatThrownBy(provider::fetchOpportunities)
                .isInstanceOf(OpportunityProviderException.class)
                .hasMessage("Lever provider failed to fetch configured sources")
                .hasMessageNotContaining("failing-site");
        assertThat(client.fetchedSources()).containsExactly("GLOBAL:failing-site");
    }

    private LeverJobClient.LeverPosting postingWithCommitment(String id, String commitment) {
        LeverJobClient.LeverPosting posting = posting(id, "Developer " + id, "https://jobs.lever.co/fake/" + id);
        posting.setCategories(categories("Remote", commitment, null));
        return posting;
    }

    private LeverJobClient.LeverPosting postingWithWorkplaceType(String id, String workplaceType) {
        LeverJobClient.LeverPosting posting = posting(id, "Developer " + id, "https://jobs.lever.co/fake/" + id);
        posting.setWorkplaceType(workplaceType);
        return posting;
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private OpportunityProviderProperties.LeverSource source(
            boolean enabled,
            String site,
            String companyName,
            LeverRegion region) {
        OpportunityProviderProperties.LeverSource source = new OpportunityProviderProperties.LeverSource();
        source.setEnabled(enabled);
        source.setSite(site);
        source.setCompanyName(companyName);
        source.setRegion(region);
        return source;
    }

    private LeverJobClient.LeverPosting posting(String id, String title, String hostedUrl) {
        LeverJobClient.LeverPosting posting = new LeverJobClient.LeverPosting();
        posting.setId(id);
        posting.setText(title);
        posting.setHostedUrl(hostedUrl);
        return posting;
    }

    private LeverJobClient.LeverCategories categories(String location, String commitment, List<String> allLocations) {
        LeverJobClient.LeverCategories categories = new LeverJobClient.LeverCategories();
        categories.setLocation(location);
        categories.setCommitment(commitment);
        categories.setAllLocations(allLocations);
        return categories;
    }

    private LeverJobClient.LeverSalaryRange salaryRange(String currency, String interval, String min, String max) {
        LeverJobClient.LeverSalaryRange salaryRange = new LeverJobClient.LeverSalaryRange();
        salaryRange.setCurrency(currency);
        salaryRange.setInterval(interval);
        if (min != null) {
            salaryRange.setMin(new BigDecimal(min));
        }
        if (max != null) {
            salaryRange.setMax(new BigDecimal(max));
        }
        return salaryRange;
    }

    private LeverJobClient.LeverListSection listSection(String text, String content) {
        LeverJobClient.LeverListSection section = new LeverJobClient.LeverListSection();
        section.setText(text);
        section.setContent(content);
        return section;
    }

    private static class StubLeverJobClient extends LeverJobClient {
        private final Map<String, List<LeverPosting>> jobsBySource = new HashMap<>();
        private final Set<String> failingSources = new HashSet<>();
        private final LinkedHashSet<String> fetchedSources = new LinkedHashSet<>();

        StubLeverJobClient() {
            super(RestClient.builder(), new OpportunityProviderProperties());
        }

        void returnJobs(String site, LeverRegion region, List<LeverPosting> postings) {
            jobsBySource.put(key(site, region), postings);
        }

        void failSource(String site, LeverRegion region) {
            failingSources.add(key(site, region));
        }

        Set<String> fetchedSources() {
            return fetchedSources;
        }

        @Override
        public List<LeverPosting> fetchJobs(String site, LeverRegion region) {
            String key = key(site, region);
            fetchedSources.add(key);
            if (failingSources.contains(key)) {
                throw new OpportunityProviderException("Lever timeout", null);
            }
            return jobsBySource.getOrDefault(key, List.of());
        }

        private static String key(String site, LeverRegion region) {
            return region.name() + ":" + site;
        }
    }
}
