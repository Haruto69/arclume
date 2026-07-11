package com.arclume.api.repository;

import com.arclume.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    java.util.Optional<User> findByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM User u WHERE u.id = ?1")
    void deleteUserById(java.util.UUID id);
}
