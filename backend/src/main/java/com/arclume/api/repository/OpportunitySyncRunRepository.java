package com.arclume.api.repository;

import com.arclume.api.domain.OpportunitySyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OpportunitySyncRunRepository extends JpaRepository<OpportunitySyncRun, UUID> {
    List<OpportunitySyncRun> findTop20ByProviderKeyOrderByStartedAtDesc(String providerKey);
}
