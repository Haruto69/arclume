package com.arclume.api.service;

import com.arclume.api.domain.Competition;
import com.arclume.api.dto.CompetitionResponse;
import com.arclume.api.dto.CompetitionSearchCriteria;
import com.arclume.api.repository.CompetitionRepository;
import com.arclume.api.repository.CompetitionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompetitionRepository competitionRepository;

    public CompetitionService(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }

    @Transactional(readOnly = true)
    public Page<CompetitionResponse> search(CompetitionSearchCriteria criteria, int page, int size) {
        validate(criteria, page, size);

        Boolean active = criteria.active() == null ? Boolean.TRUE : criteria.active();
        Specification<Competition> specification = Specification.allOf(
                CompetitionSpecifications.hasKeyword(criteria.keyword()),
                CompetitionSpecifications.hasSourceProvider(criteria.sourceProvider()),
                CompetitionSpecifications.hasCompetitionFormat(criteria.competitionFormat()),
                CompetitionSpecifications.hasPhase(criteria.phase()),
                CompetitionSpecifications.hasCountry(criteria.country()),
                CompetitionSpecifications.hasCity(criteria.city()),
                CompetitionSpecifications.startsAtOrAfter(criteria.startsAfter()),
                CompetitionSpecifications.startsAtOrBefore(criteria.startsBefore()),
                CompetitionSpecifications.hasActiveStatus(active)
        );

        Sort sort = Sort.by(
                Sort.Order.asc("startsAt"),
                Sort.Order.asc("title"),
                Sort.Order.asc("id")
        );
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
        return competitionRepository.findAll(specification, pageable).map(CompetitionResponse::from);
    }

    private void validate(CompetitionSearchCriteria criteria, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        if (criteria.startsAfter() != null
                && criteria.startsBefore() != null
                && criteria.startsAfter().isAfter(criteria.startsBefore())) {
            throw new IllegalArgumentException("Start timestamp range is invalid");
        }
    }
}
