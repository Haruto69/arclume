package com.arclume.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserSkillId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "skill_id")
    private UUID skillId;

    public UserSkillId() {}

    public UserSkillId(UUID userId, UUID skillId) {
        this.userId = userId;
        this.skillId = skillId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public void setSkillId(UUID skillId) {
        this.skillId = skillId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSkillId that = (UserSkillId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, skillId);
    }
}
