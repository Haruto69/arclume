package com.arclume.api.service.opportunity;

import com.arclume.api.domain.Competition;
import com.arclume.api.repository.CompetitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CompetitionOpportunityWriter {

    private final CompetitionRepository competitionRepository;

    public CompetitionOpportunityWriter(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpportunityPersistResult upsert(
            String providerKey,
            NormalizedCompetitionOpportunity record,
            Instant syncedAt) {
        Competition competition = competitionRepository.findBySourceProviderAndSourceId(
                        providerKey,
                        record.sourceId())
                .orElseGet(Competition::new);
        boolean created = competition.getId() == null;

        if (created) {
            competition.setSourceProvider(providerKey);
            competition.setSourceId(record.sourceId());
        }

        competition.setTitle(record.title());
        competition.setOrganizer(record.organizer());
        competition.setCompetitionFormat(record.competitionFormat());
        competition.setPhase(record.phase());
        competition.setKind(record.kind());
        competition.setDifficulty(record.difficulty());
        competition.setCity(record.city());
        competition.setCountry(record.country());
        competition.setStartsAt(record.startsAt());
        competition.setEndsAt(record.endsAt());
        competition.setDurationSeconds(record.durationSeconds());
        competition.setExternalUrl(record.sourceUrl());
        competition.setAttributionLabel(record.attributionLabel());
        competition.setSyncedAt(syncedAt);
        competition.setActive(record.active());

        competitionRepository.save(competition);
        return created ? OpportunityPersistResult.CREATED : OpportunityPersistResult.UPDATED;
    }
}
