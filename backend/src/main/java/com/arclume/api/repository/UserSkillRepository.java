package com.arclume.api.repository;

import com.arclume.api.domain.UserSkill;
import com.arclume.api.domain.UserSkillId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, UserSkillId> {
    List<UserSkill> findByUserId(UUID userId);
}
