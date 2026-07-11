package com.arclume.api.repository;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.Application;
import com.arclume.api.domain.ApplicationStatus;
import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CoreDataModelingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    void whenUserIsSaved_thenItCanBeRetrieved() {
        User user = new User();
        user.setEmail("test1@example.com");
        user.setFirstName("Alice");
        user.setLastName("Smith");

        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();

        Optional<User> retrieved = userRepository.findById(savedUser.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getEmail()).isEqualTo("test1@example.com");
    }

    @Test
    void whenDuplicateEmailIsSaved_thenThrowDataIntegrityViolationException() {
        User user1 = new User();
        user1.setEmail("duplicate@example.com");
        user1.setFirstName("Alice");
        user1.setLastName("Smith");
        userRepository.saveAndFlush(user1);

        User user2 = new User();
        user2.setEmail("duplicate@example.com");
        user2.setFirstName("Bob");
        user2.setLastName("Jones");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void whenJobIsSaved_thenItCanBeRetrieved() {
        Job job = new Job();
        job.setTitle("Software Engineer");
        job.setCompany("Tech Corp");
        job.setEmploymentType(EmploymentType.FULL_TIME);

        Job savedJob = jobRepository.saveAndFlush(job);

        assertThat(savedJob.getId()).isNotNull();
        assertThat(savedJob.getActive()).isTrue();

        Optional<Job> retrieved = jobRepository.findById(savedJob.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getTitle()).isEqualTo("Software Engineer");
    }

    @Test
    void whenApplicationIsSaved_thenRelationshipsLoadCorrectly() {
        User user = new User();
        user.setEmail("appuser@example.com");
        user.setFirstName("Carol");
        user.setLastName("White");
        user = userRepository.saveAndFlush(user);

        Job job = new Job();
        job.setTitle("Data Scientist");
        job.setCompany("Data Inc");
        job = jobRepository.saveAndFlush(job);

        Application app = new Application();
        app.setUser(user);
        app.setJob(job);
        app.setStatus(ApplicationStatus.APPLIED);
        app = applicationRepository.saveAndFlush(app);

        assertThat(app.getId()).isNotNull();

        Optional<Application> retrieved = applicationRepository.findById(app.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(retrieved.get().getJob().getId()).isEqualTo(job.getId());
    }

    @Test
    void whenDuplicateApplicationIsSaved_thenThrowDataIntegrityViolationException() {
        User user = new User();
        user.setEmail("dupappuser@example.com");
        user.setFirstName("Dave");
        user.setLastName("Brown");
        user = userRepository.saveAndFlush(user);

        Job job = new Job();
        job.setTitle("Product Manager");
        job.setCompany("Product Inc");
        job = jobRepository.saveAndFlush(job);

        Application app1 = new Application();
        app1.setUser(user);
        app1.setJob(job);
        app1.setStatus(ApplicationStatus.APPLIED);
        applicationRepository.saveAndFlush(app1);

        Application app2 = new Application();
        app2.setUser(user);
        app2.setJob(job);
        app2.setStatus(ApplicationStatus.SAVED);

        assertThatThrownBy(() -> applicationRepository.saveAndFlush(app2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void whenUserIsDeleted_thenApplicationsAreCascaded() {
        User user = new User();
        user.setEmail("deleteuser@example.com");
        user.setFirstName("Eve");
        user.setLastName("Black");
        user = userRepository.saveAndFlush(user);

        Job job = new Job();
        job.setTitle("Designer");
        job.setCompany("Design Inc");
        job = jobRepository.saveAndFlush(job);

        Application app = new Application();
        app.setUser(user);
        app.setJob(job);
        app.setStatus(ApplicationStatus.APPLIED);
        app = applicationRepository.saveAndFlush(app);

        // Delete user
        userRepository.deleteById(user.getId());
        userRepository.flush();

        // Application should be deleted
        Optional<Application> retrievedApp = applicationRepository.findById(app.getId());
        assertThat(retrievedApp).isNotPresent();

        // Job should remain
        Optional<Job> retrievedJob = jobRepository.findById(job.getId());
        assertThat(retrievedJob).isPresent();
    }

    @Test
    void whenJobIsDeleted_thenThrowDataIntegrityViolationExceptionIfApplicationsExist() {
        User user = new User();
        user.setEmail("jobdeleteuser@example.com");
        user.setFirstName("Frank");
        user.setLastName("Green");
        user = userRepository.saveAndFlush(user);

        Job job = new Job();
        job.setTitle("QA Engineer");
        job.setCompany("QA Inc");
        job = jobRepository.saveAndFlush(job);

        Application app = new Application();
        app.setUser(user);
        app.setJob(job);
        app.setStatus(ApplicationStatus.APPLIED);
        applicationRepository.saveAndFlush(app);

        // Deleting job should fail because of RESTRICT foreign key
        final Job jobToDelete = job;
        assertThatThrownBy(() -> {
            jobRepository.deleteById(jobToDelete.getId());
            jobRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
