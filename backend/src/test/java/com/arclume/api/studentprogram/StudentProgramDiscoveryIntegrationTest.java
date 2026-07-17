package com.arclume.api.studentprogram;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Role;
import com.arclume.api.domain.StudentProgram;
import com.arclume.api.domain.StudentProgramMode;
import com.arclume.api.domain.StudentProgramType;
import com.arclume.api.domain.User;
import com.arclume.api.repository.StudentProgramRepository;
import com.arclume.api.repository.UserRepository;
import com.arclume.api.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
class StudentProgramDiscoveryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentProgramRepository studentProgramRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Cookie userCookie;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("student-program-user@example.com");
        user.setFirstName("Program");
        user.setLastName("Explorer");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        user = userRepository.saveAndFlush(user);
        userCookie = new Cookie("ARCLUME_ACCESS_TOKEN", jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name()
        ));
    }

    @Test
    void seededProgramsAreVisibleAsSafeDtosWithJsonArrays() throws Exception {
        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("keyword", "GitHub Student Developer Pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("GitHub Student Developer Pack"))
                .andExpect(jsonPath("$.content[0].company").value("GitHub"))
                .andExpect(jsonPath("$.content[0].sourceProvider").value("OFFICIAL"))
                .andExpect(jsonPath("$.content[0].benefitTypes").isArray())
                .andExpect(jsonPath("$.content[0].tags").isArray())
                .andExpect(jsonPath("$.content[0].lastVerifiedAt").value("2026-07-17"))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].sourceId").doesNotExist())
                .andExpect(jsonPath("$.content[0].hibernateLazyInitializer").doesNotExist());

        Integer arrayRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM student_programs "
                        + "WHERE source_provider = 'OFFICIAL' "
                        + "AND jsonb_typeof(benefit_types) = 'array' "
                        + "AND jsonb_typeof(tags) = 'array'",
                Integer.class
        );
        assertThat(arrayRows).isEqualTo(8);
    }

    @Test
    void defaultListExcludesInactivePrograms() throws Exception {
        StudentProgram inactive = program("Phase Twelve Archived Program", "Archived Program Lab");
        inactive.setActive(false);
        studentProgramRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("keyword", "Phase Twelve Archived Program"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("keyword", "Phase Twelve Archived Program")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inactive.getId().toString()));
    }

    @Test
    void filtersByKeywordAndCompany() throws Exception {
        StudentProgram target = program("Phase Twelve Accessibility Academy", "Inclusive Systems Studio");
        target.setDescription("Build accessible interfaces with guided practice.");
        target.setBenefitSummary("Portfolio practice may be available.");
        studentProgramRepository.saveAndFlush(target);

        studentProgramRepository.saveAndFlush(program("Different Academy", "Other Studio"));

        assertSingleFilter("keyword", "accessible interfaces", target);
        assertSingleFilter("keyword", "portfolio practice", target);
        assertSingleFilter("company", "inclusive systems", target);
    }

    @Test
    void filtersByTypeModeBenefitAndGeography() throws Exception {
        StudentProgram target = program("Phase Twelve Target Program", "Phase Twelve Filter Lab");
        target.setProgramType(StudentProgramType.CERTIFICATION);
        target.setMode(StudentProgramMode.HYBRID);
        target.setBenefitTypes(List.of("CERTIFICATE", "TRAINING"));
        target.setRegion("Karnataka");
        target.setCountry("India");

        StudentProgram decoy = program("Phase Twelve Decoy Program", "Phase Twelve Filter Lab");
        decoy.setProgramType(StudentProgramType.COMMUNITY);
        decoy.setMode(StudentProgramMode.IN_PERSON);
        decoy.setBenefitTypes(List.of("NETWORKING"));
        decoy.setRegion("England");
        decoy.setCountry("United Kingdom");
        studentProgramRepository.saveAllAndFlush(List.of(target, decoy));

        assertFilterWithinCompany("programType", "CERTIFICATION", target);
        assertFilterWithinCompany("mode", "HYBRID", target);
        assertFilterWithinCompany("benefitType", "CERTIFICATE", target);
        assertFilterWithinCompany("region", "Karnataka", target);
        assertFilterWithinCompany("country", "India", target);
    }

    @Test
    void filtersByAlwaysOpenAndInclusiveDeadlineRange() throws Exception {
        StudentProgram alwaysOpen = program("Phase Twelve Evergreen", "Phase Twelve Date Lab");
        alwaysOpen.setAlwaysOpen(true);

        StudentProgram inRange = program("Phase Twelve Dated", "Phase Twelve Date Lab");
        inRange.setApplicationDeadline(LocalDate.of(2026, 10, 15));

        StudentProgram outsideRange = program("Phase Twelve Later", "Phase Twelve Date Lab");
        outsideRange.setApplicationDeadline(LocalDate.of(2026, 11, 1));
        studentProgramRepository.saveAllAndFlush(List.of(alwaysOpen, inRange, outsideRange));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("company", "Phase Twelve Date Lab")
                        .param("alwaysOpen", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(alwaysOpen.getId().toString()));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("company", "Phase Twelve Date Lab")
                        .param("applicationDeadlineAfter", "2026-10-15")
                        .param("applicationDeadlineBefore", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inRange.getId().toString()));
    }

    @Test
    void paginatesAndSortsAlwaysOpenThenUpcomingDeterministically() throws Exception {
        StudentProgram later = program("Later Dated Program", "Phase Twelve Sorting Lab");
        later.setActive(false);
        later.setApplicationDeadline(LocalDate.of(2026, 12, 1));

        StudentProgram earlier = program("Earlier Dated Program", "Phase Twelve Sorting Lab");
        earlier.setActive(false);
        earlier.setApplicationDeadline(LocalDate.of(2026, 8, 1));

        StudentProgram evergreen = program("Evergreen Program", "Phase Twelve Sorting Lab");
        evergreen.setActive(false);
        evergreen.setAlwaysOpen(true);
        studentProgramRepository.saveAllAndFlush(List.of(later, earlier, evergreen));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("company", "Phase Twelve Sorting Lab")
                        .param("active", "false")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(evergreen.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(earlier.getId().toString()));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("company", "Phase Twelve Sorting Lab")
                        .param("active", "false")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(later.getId().toString()));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void usesIdAsFinalSortTieBreaker() throws Exception {
        UUID firstId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        insertSortTieProgram(secondId, "tie-breaker-second");
        insertSortTieProgram(firstId, "tie-breaker-first");

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("company", "Phase Twelve Tie Breaker Lab")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(firstId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(secondId.toString()));
    }

    @Test
    void rejectsInvalidFiltersWithReadableErrors() throws Exception {
        assertInvalidParameter("programType", "INTERNSHIP");
        assertInvalidParameter("mode", "REMOTE");
        assertInvalidParameter("benefitType", "MONEY");
        assertInvalidParameter("alwaysOpen", "maybe");
        assertInvalidParameter("applicationDeadlineBefore", "not-a-date");

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Page must be zero or greater"));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Size must be at least 1"));

        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("applicationDeadlineAfter", "2026-12-01")
                        .param("applicationDeadlineBefore", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Application deadline range is invalid"));
    }

    @Test
    void nullableAndBlankSourceIdsDoNotConflict() {
        StudentProgram first = program("First Unidentified Program", "Program Lab");
        first.setSourceId(null);
        StudentProgram second = program("Second Unidentified Program", "Program Lab");
        second.setSourceId("   ");

        assertThat(second.getSourceId()).isNull();
        studentProgramRepository.saveAllAndFlush(List.of(first, second));

        assertThat(studentProgramRepository.findAll()).contains(first, second);
    }

    @Test
    void duplicateNonNullSourceIdsAreRejected() {
        StudentProgram first = program("Imported Program", "Program Lab");
        first.setSourceProvider("official_test");
        first.setSourceId("shared-provider-id");
        studentProgramRepository.saveAndFlush(first);

        StudentProgram duplicate = program("Duplicate Imported Program", "Program Lab");
        duplicate.setSourceProvider(" OFFICIAL_TEST ");
        duplicate.setSourceId(" shared-provider-id ");

        assertThatThrownBy(() -> studentProgramRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unauthenticatedAccessIsRejectedLikeOtherDiscoveryApis() throws Exception {
        mockMvc.perform(get("/api/v1/student-programs"))
                .andExpect(status().isUnauthorized());
    }

    private void assertSingleFilter(String name, String value, StudentProgram expected) throws Exception {
        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param(name, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expected.getId().toString()));
    }

    private void assertFilterWithinCompany(String name, String value, StudentProgram expected) throws Exception {
        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param("company", "Phase Twelve Filter Lab")
                        .param(name, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expected.getId().toString()));
    }

    private void assertInvalidParameter(String name, String value) throws Exception {
        mockMvc.perform(get("/api/v1/student-programs")
                        .cookie(userCookie)
                        .param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for " + name));
    }

    private void insertSortTieProgram(UUID id, String sourceId) {
        jdbcTemplate.update("""
                INSERT INTO student_programs (
                    id, created_at, updated_at, title, company, program_type, mode,
                    always_open, external_url, source_provider, source_id,
                    benefit_types, tags, is_active
                ) VALUES (?, NOW(), NOW(), ?, ?, 'LEARNING', 'ONLINE', FALSE, ?, 'TEST_SOURCE', ?,
                    '[]'::jsonb, '[]'::jsonb, FALSE)
                """,
                id,
                "Identical Sort Title",
                "Phase Twelve Tie Breaker Lab",
                "https://example.test/student-programs/" + sourceId,
                sourceId
        );
    }

    private StudentProgram program(String title, String company) {
        StudentProgram program = new StudentProgram();
        program.setTitle(title);
        program.setCompany(company);
        program.setProgramType(StudentProgramType.LEARNING);
        program.setMode(StudentProgramMode.ONLINE);
        program.setRegion("Global");
        program.setCountry("Online");
        program.setEligibility("Test eligibility requirements.");
        program.setBenefitSummary("Test benefits may be available.");
        program.setAlwaysOpen(false);
        program.setExternalUrl("https://example.test/student-programs/" + UUID.randomUUID());
        program.setSourceProvider("TEST_SOURCE");
        program.setSourceId(UUID.randomUUID().toString());
        program.setDescription("Test data for student program discovery integration tests.");
        program.setBenefitTypes(List.of("TRAINING"));
        program.setTags(List.of("test data"));
        program.setActive(true);
        program.setLastVerifiedAt(LocalDate.of(2026, 7, 17));
        return program;
    }
}
