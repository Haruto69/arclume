package com.arclume.test.probe;

import com.arclume.api.config.BaseIntegrationTest;
import com.arclume.api.domain.BaseEntity;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        classes = JpaConfigurationIntegrationTest.TestConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop"
)
@EntityScan(basePackageClasses = {BaseEntity.class, TestEntity.class})
@EnableJpaRepositories(basePackageClasses = TestEntityRepository.class)
public class JpaConfigurationIntegrationTest extends BaseIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(com.arclume.api.config.JpaConfig.class)
    static class TestConfig {
    }

    @Autowired
    private TestEntityRepository repository;

    @Test
    void whenEntityIsSaved_thenUuidAndAuditFieldsArePopulated() {
        TestEntity entity = new TestEntity();
        entity.setName("Probe");

        TestEntity savedEntity = repository.saveAndFlush(entity);

        assertThat(savedEntity.getId()).isNotNull();
        assertThat(savedEntity.getCreatedAt()).isNotNull();
        assertThat(savedEntity.getUpdatedAt()).isNotNull();
        assertThat(savedEntity.getName()).isEqualTo("Probe");
    }
}

@Entity
class TestEntity extends BaseEntity {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

interface TestEntityRepository extends JpaRepository<TestEntity, UUID> {
}
