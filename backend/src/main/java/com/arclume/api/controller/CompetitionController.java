package com.arclume.api.controller;

import com.arclume.api.domain.CompetitionFormat;
import com.arclume.api.domain.CompetitionPhase;
import com.arclume.api.dto.CompetitionResponse;
import com.arclume.api.dto.CompetitionSearchCriteria;
import com.arclume.api.service.CompetitionService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/competitions")
public class CompetitionController {

    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @GetMapping
    public ResponseEntity<Page<CompetitionResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceProvider,
            @RequestParam(required = false) CompetitionFormat competitionFormat,
            @RequestParam(required = false) CompetitionPhase phase,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Instant startsAfter,
            @RequestParam(required = false) Instant startsBefore,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CompetitionSearchCriteria criteria = new CompetitionSearchCriteria(
                keyword,
                sourceProvider,
                competitionFormat,
                phase,
                country,
                city,
                startsAfter,
                startsBefore,
                active
        );
        return ResponseEntity.ok(competitionService.search(criteria, page, size));
    }
}
