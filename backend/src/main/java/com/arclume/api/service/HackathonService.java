package com.arclume.api.service;

import com.arclume.api.domain.Hackathon;
import com.arclume.api.dto.HackathonResponse;
import com.arclume.api.dto.HackathonSearchCriteria;
import com.arclume.api.repository.HackathonRepository;
import com.arclume.api.repository.HackathonSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class HackathonService {

    private static final int MAX_PAGE_SIZE = 100;

    private final HackathonRepository hackathonRepository;

    public HackathonService(HackathonRepository hackathonRepository) {
        this.hackathonRepository = hackathonRepository;
    }

    @Transactional(readOnly = true)
    public Page<HackathonResponse> search(HackathonSearchCriteria criteria, int page, int size) {
        validate(criteria, page, size);

        Boolean active = criteria.active() == null ? Boolean.TRUE : criteria.active();
        Specification<Hackathon> specification = Specification.allOf(
                HackathonSpecifications.hasKeyword(criteria.keyword()),
                HackathonSpecifications.hasOrganizer(criteria.organizer()),
                HackathonSpecifications.hasCity(criteria.city()),
                HackathonSpecifications.hasRegion(criteria.region()),
                HackathonSpecifications.hasCountry(criteria.country()),
                HackathonSpecifications.hasSourceProvider(criteria.sourceProvider()),
                HackathonSpecifications.hasMode(criteria.mode()),
                HackathonSpecifications.hasOrganizerType(criteria.organizerType()),
                HackathonSpecifications.hasMinimumPrize(criteria.minPrizePoolAmount()),
                HackathonSpecifications.hasMaximumPrize(criteria.maxPrizePoolAmount()),
                HackathonSpecifications.startsOnOrAfter(criteria.startsAfter()),
                HackathonSpecifications.startsOnOrBefore(criteria.startsBefore()),
                HackathonSpecifications.hasActiveStatus(active)
        );

        Sort sort = Sort.by(
                Sort.Order.asc("startDate").nullsLast(),
                Sort.Order.asc("title"),
                Sort.Order.asc("id")
        );
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
        return hackathonRepository.findAll(specification, pageable).map(HackathonResponse::from);
    }

    private void validate(HackathonSearchCriteria criteria, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        validateAmount(criteria.minPrizePoolAmount(), "Minimum prize pool");
        validateAmount(criteria.maxPrizePoolAmount(), "Maximum prize pool");
        if (criteria.minPrizePoolAmount() != null
                && criteria.maxPrizePoolAmount() != null
                && criteria.minPrizePoolAmount().compareTo(criteria.maxPrizePoolAmount()) > 0) {
            throw new IllegalArgumentException("Minimum prize pool cannot exceed maximum prize pool");
        }
        if (criteria.startsAfter() != null
                && criteria.startsBefore() != null
                && criteria.startsAfter().isAfter(criteria.startsBefore())) {
            throw new IllegalArgumentException("Start date range is invalid");
        }
    }

    private void validateAmount(BigDecimal amount, String label) {
        if (amount != null && amount.signum() < 0) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}

