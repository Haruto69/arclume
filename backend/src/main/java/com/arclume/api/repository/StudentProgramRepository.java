package com.arclume.api.repository;

import com.arclume.api.domain.StudentProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface StudentProgramRepository extends JpaRepository<StudentProgram, UUID>, JpaSpecificationExecutor<StudentProgram> {
    Optional<StudentProgram> findBySourceProviderAndSourceId(String sourceProvider, String sourceId);
}
