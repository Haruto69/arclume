package com.arclume.api.hackathon;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Hackathon;
import com.arclume.api.domain.HackathonMode;
import com.arclume.api.domain.HackathonOrganizerType;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.repository.HackathonRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ai.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class HackathonDiscoveryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HackathonRepository hackathonRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    private Cookie userCookie;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("hackathon-user@example.com");
        user.setFirstName("Hackathon");
        user.setLastName("Explorer");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        user = userRepository.saveAndFlush(user);
        userCookie = new Cookie("ARCLUME_SESSION", sessionService.create(user, "integration-test", "127.0.0.1").token());
    }

    @Test
    void defaultListReturnsOnlyActiveHackathonsAsSafeDtos() throws Exception {
        Hackathon active = hackathon("Open Source Sprint", "Arclume Community", LocalDate.of(2026, 8, 10));
        active.setTags(List.of("open source", "Java"));
        active.setDescription("Build maintainable tools for developers.");
        hackathonRepository.saveAndFlush(active);

        Hackathon inactive = hackathon("Archived Challenge", "Past Events", LocalDate.of(2026, 7, 1));
        inactive.setActive(false);
        hackathonRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/hackathons").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$.content[0].title").value("Open Source Sprint"))
                .andExpect(jsonPath("$.content[0].sourceProvider").value("TEST_SOURCE"))
                .andExpect(jsonPath("$.content[0].tags[0]").value("open source"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].sourceId").doesNotExist())
                .andExpect(jsonPath("$.content[0].hibernateLazyInitializer").doesNotExist());

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inactive.getId().toString()));
    }

    @Test
    void filtersByGeographyOrganizerSourceModeAndOrganizerType() throws Exception {
        Hackathon bengaluru = hackathon("Cloud Builders", "Arclume Labs", LocalDate.of(2026, 9, 5));
        bengaluru.setCity("Bengaluru");
        bengaluru.setRegion("Karnataka");
        bengaluru.setCountry("India");
        bengaluru.setMode(HackathonMode.HYBRID);
        bengaluru.setOrganizerType(HackathonOrganizerType.COMPANY);
        bengaluru.setSourceProvider("DEVPOST_TEST");
        hackathonRepository.saveAndFlush(bengaluru);

        Hackathon london = hackathon("Campus Climate Jam", "Northbridge University", LocalDate.of(2026, 9, 12));
        london.setCity("London");
        london.setRegion("England");
        london.setCountry("United Kingdom");
        london.setMode(HackathonMode.IN_PERSON);
        london.setOrganizerType(HackathonOrganizerType.COLLEGE);
        london.setSourceProvider("MLH_TEST");
        hackathonRepository.saveAndFlush(london);

        assertSingleFilter("city", "bengaluru", bengaluru);
        assertSingleFilter("region", "Karnataka", bengaluru);
        assertSingleFilter("country", "India", bengaluru);
        assertSingleFilter("organizer", "arclume", bengaluru);
        assertSingleFilter("sourceProvider", "devpost", bengaluru);
        assertSingleFilter("mode", "HYBRID", bengaluru);
        assertSingleFilter("organizerType", "COMPANY", bengaluru);
    }

    @Test
    void filtersByPrizePoolRange() throws Exception {
        Hackathon starter = hackathon("Starter Prize", "Community Guild", LocalDate.of(2026, 8, 20));
        starter.setPrizePoolAmount(new BigDecimal("500.00"));
        starter.setPrizePoolCurrency("USD");
        hackathonRepository.saveAndFlush(starter);

        Hackathon major = hackathon("Major Prize", "Innovation Office", LocalDate.of(2026, 8, 21));
        major.setPrizePoolAmount(new BigDecimal("5000.00"));
        major.setPrizePoolCurrency("USD");
        hackathonRepository.saveAndFlush(major);

        Hackathon undisclosed = hackathon("Community Build", "Local Group", LocalDate.of(2026, 8, 22));
        hackathonRepository.saveAndFlush(undisclosed);

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("minPrizePoolAmount", "1000")
                        .param("maxPrizePoolAmount", "6000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(major.getId().toString()))
                .andExpect(jsonPath("$.content[0].prizePoolAmount").value(5000.0));
    }

    @Test
    void filtersByInclusiveDateRangeAndKeyword() throws Exception {
        Hackathon target = hackathon("Accessible Web Challenge", "Web Community", LocalDate.of(2026, 10, 15));
        target.setDescription("A practical accessibility event.");
        hackathonRepository.saveAndFlush(target);

        hackathonRepository.saveAndFlush(hackathon(
                "Earlier Challenge",
                "Web Community",
                LocalDate.of(2026, 9, 30)
        ));
        hackathonRepository.saveAndFlush(hackathon(
                "Later Challenge",
                "Web Community",
                LocalDate.of(2026, 11, 1)
        ));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("keyword", "accessible web")
                        .param("startsAfter", "2026-10-01")
                        .param("startsBefore", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(target.getId().toString()))
                .andExpect(jsonPath("$.content[0].startDate").value("2026-10-15"));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("keyword", "practical accessibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(target.getId().toString()));
    }

    @Test
    void paginatesWithUpcomingEventsFirstAndCapsPageSize() throws Exception {
        Hackathon later = hackathon("Later Event", "Organizer", LocalDate.of(2026, 12, 1));
        Hackathon earlier = hackathon("Earlier Event", "Organizer", LocalDate.of(2026, 8, 1));
        Hackathon undated = hackathon("Undated Event", "Organizer", null);
        hackathonRepository.saveAllAndFlush(List.of(later, earlier, undated));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(earlier.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(later.getId().toString()));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(undated.getId().toString()));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsInvalidRangesWithReadableErrors() throws Exception {
        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("minPrizePoolAmount", "5000")
                        .param("maxPrizePoolAmount", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Minimum prize pool cannot exceed maximum prize pool"));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("startsAfter", "2026-12-01")
                        .param("startsBefore", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Start date range is invalid"));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("minPrizePoolAmount", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Minimum prize pool cannot be negative"));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("mode", "REMOTE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for mode"));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("startsAfter", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for startsAfter"));

        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param("minPrizePoolAmount", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for minPrizePoolAmount"));
    }

    @Test
    void nullableAndBlankSourceIdsDoNotConflict() {
        Hackathon first = hackathon("First Unidentified Event", "Organizer", LocalDate.of(2026, 10, 1));
        first.setSourceId(null);
        Hackathon second = hackathon("Second Unidentified Event", "Organizer", LocalDate.of(2026, 10, 2));
        second.setSourceId("   ");

        assertThat(second.getSourceId()).isNull();
        hackathonRepository.saveAllAndFlush(List.of(first, second));

        assertThat(hackathonRepository.count()).isEqualTo(2);
    }

    @Test
    void duplicateNonNullSourceIdsAreRejected() {
        Hackathon first = hackathon("Imported Event", "Organizer", LocalDate.of(2026, 10, 1));
        first.setSourceProvider("devpost_test");
        first.setSourceId("shared-provider-id");
        hackathonRepository.saveAndFlush(first);

        Hackathon duplicate = hackathon("Duplicate Imported Event", "Organizer", LocalDate.of(2026, 10, 2));
        duplicate.setSourceProvider(" DEVPOST_TEST ");
        duplicate.setSourceId(" shared-provider-id ");

        assertThatThrownBy(() -> hackathonRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unauthenticatedAccessIsRejectedLikeJobs() throws Exception {
        mockMvc.perform(get("/api/v1/hackathons"))
                .andExpect(status().isUnauthorized());
    }

    private void assertSingleFilter(String name, String value, Hackathon expected) throws Exception {
        mockMvc.perform(get("/api/v1/hackathons")
                        .cookie(userCookie)
                        .param(name, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expected.getId().toString()));
    }

    private Hackathon hackathon(String title, String organizer, LocalDate startDate) {
        Hackathon hackathon = new Hackathon();
        hackathon.setTitle(title);
        hackathon.setOrganizer(organizer);
        hackathon.setOrganizerType(HackathonOrganizerType.COMMUNITY);
        hackathon.setCity("Pune");
        hackathon.setRegion("Maharashtra");
        hackathon.setCountry("India");
        hackathon.setMode(HackathonMode.ONLINE);
        hackathon.setRegistrationDeadline(startDate == null ? null : startDate.minusDays(7));
        hackathon.setStartDate(startDate);
        hackathon.setEndDate(startDate == null ? null : startDate.plusDays(2));
        hackathon.setExternalUrl("https://example.test/hackathons/" + UUID.randomUUID());
        hackathon.setSourceProvider("TEST_SOURCE");
        hackathon.setSourceId(UUID.randomUUID().toString());
        hackathon.setDescription("Test data for hackathon discovery integration tests.");
        hackathon.setTags(List.of("test data"));
        hackathon.setActive(true);
        return hackathon;
    }
}

