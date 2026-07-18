package com.arclume.api.competition;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Competition;
import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.User;
import com.arclume.api.repository.CompetitionRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ai.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CompetitionDiscoveryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    private Cookie userCookie;

    @BeforeEach
    void setUp() {
        competitionRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("competition-user@example.com");
        user.setFirstName("Competition");
        user.setLastName("Explorer");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        user = userRepository.saveAndFlush(user);
        userCookie = new Cookie("ARCLUME_SESSION", sessionService.create(user, "integration-test", "127.0.0.1").token());
    }

    @Test
    void defaultListReturnsOnlyActiveCompetitionsWithAttributionAndCanonicalUrl() throws Exception {
        Competition active = competition("Open Round", "1001", CompetitionPhase.BEFORE, true,
                Instant.parse("2026-08-01T12:00:00Z"));
        competitionRepository.saveAndFlush(active);

        Competition inactive = competition("Archived Round", "1002", CompetitionPhase.FINISHED, false,
                Instant.parse("2026-07-01T12:00:00Z"));
        competitionRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/competitions").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$.content[0].title").value("Open Round"))
                .andExpect(jsonPath("$.content[0].externalUrl").value("https://codeforces.com/contest/1001"))
                .andExpect(jsonPath("$.content[0].sourceProvider").value("CODEFORCES"))
                .andExpect(jsonPath("$.content[0].sourceId").value("1001"))
                .andExpect(jsonPath("$.content[0].attributionLabel").value("Contest data from Codeforces"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inactive.getId().toString()));
    }

    @Test
    void filtersByKeywordFormatPhaseLocationSourceAndStartRange() throws Exception {
        Competition target = competition("Educational Round", "2001", CompetitionPhase.CODING, true,
                Instant.parse("2026-08-05T12:00:00Z"));
        target.setCompetitionFormat(CompetitionFormat.IOI);
        target.setKind("Educational");
        target.setCity("Warsaw");
        target.setCountry("Poland");
        target.setSourceProvider("CODEFORCES");
        competitionRepository.saveAndFlush(target);

        Competition other = competition("Global Championship", "2002", CompetitionPhase.BEFORE, true,
                Instant.parse("2026-09-05T12:00:00Z"));
        other.setCompetitionFormat(CompetitionFormat.ICPC);
        other.setKind("Championship");
        other.setCity("London");
        other.setCountry("United Kingdom");
        other.setSourceProvider("OTHER_SOURCE");
        competitionRepository.saveAndFlush(other);

        assertSingleFilter("keyword", "educational", target);
        assertSingleFilter("competitionFormat", "IOI", target);
        assertSingleFilter("phase", "CODING", target);
        assertSingleFilter("city", "warsaw", target);
        assertSingleFilter("country", "poland", target);
        assertSingleFilter("sourceProvider", "codeforces", target);

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("startsAfter", "2026-08-01T00:00:00Z")
                        .param("startsBefore", "2026-08-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(target.getId().toString()));
    }

    @Test
    void paginatesWithDeterministicStartTitleAndIdSortAndCapsPageSize() throws Exception {
        Competition beta = competition("Beta Round", "3001", CompetitionPhase.BEFORE, true,
                Instant.parse("2026-08-02T12:00:00Z"));
        Competition alpha = competition("Alpha Round", "3002", CompetitionPhase.BEFORE, true,
                Instant.parse("2026-08-01T12:00:00Z"));
        Competition alphaLaterId = competition("Alpha Round", "3003", CompetitionPhase.BEFORE, true,
                Instant.parse("2026-08-01T12:00:00Z"));
        competitionRepository.saveAllAndFlush(List.of(beta, alpha, alphaLaterId));

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].startsAt").value("2026-08-01T12:00:00Z"))
                .andExpect(jsonPath("$.content[0].title").value("Alpha Round"))
                .andExpect(jsonPath("$.content[1].startsAt").value("2026-08-01T12:00:00Z"))
                .andExpect(jsonPath("$.content[1].title").value("Alpha Round"));

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(beta.getId().toString()));

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsInvalidPagingRangesEnumsAndInstants() throws Exception {
        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Page must be zero or greater"));

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Size must be at least 1"));

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("startsAfter", "2026-09-01T00:00:00Z")
                        .param("startsBefore", "2026-08-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Start timestamp range is invalid"));

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("competitionFormat", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for competitionFormat"));

        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param("startsAfter", "not-an-instant"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for startsAfter"));
    }

    @Test
    void unauthenticatedAccessIsRejectedLikeJobsAndHackathons() throws Exception {
        mockMvc.perform(get("/api/v1/competitions"))
                .andExpect(status().isUnauthorized());
    }

    private void assertSingleFilter(String name, String value, Competition expected) throws Exception {
        mockMvc.perform(get("/api/v1/competitions")
                        .cookie(userCookie)
                        .param(name, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expected.getId().toString()));
    }

    private Competition competition(
            String title,
            String sourceId,
            CompetitionPhase phase,
            boolean active,
            Instant startsAt) {
        Competition competition = new Competition();
        competition.setTitle(title);
        competition.setOrganizer("Codeforces");
        competition.setCompetitionFormat(CompetitionFormat.CF);
        competition.setPhase(phase);
        competition.setKind("Official");
        competition.setDifficulty(3);
        competition.setCity("Bengaluru");
        competition.setCountry("India");
        competition.setStartsAt(startsAt);
        competition.setEndsAt(startsAt.plusSeconds(7200));
        competition.setDurationSeconds(7200);
        competition.setExternalUrl("https://codeforces.com/contest/" + sourceId);
        competition.setSourceProvider("CODEFORCES");
        competition.setSourceId(sourceId);
        competition.setAttributionLabel("Contest data from Codeforces");
        competition.setSyncedAt(Instant.parse("2026-07-18T12:00:00Z"));
        competition.setActive(active);
        return competition;
    }
}
