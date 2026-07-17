package com.arclume.api.controller;

import com.arclume.api.domain.HackathonMode;
import com.arclume.api.domain.HackathonOrganizerType;
import com.arclume.api.dto.HackathonResponse;
import com.arclume.api.dto.HackathonSearchCriteria;
import com.arclume.api.service.HackathonService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/hackathons")
public class HackathonController {

    private final HackathonService hackathonService;

    public HackathonController(HackathonService hackathonService) {
        this.hackathonService = hackathonService;
    }

    @GetMapping
    public ResponseEntity<Page<HackathonResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String organizer,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String sourceProvider,
            @RequestParam(required = false) HackathonMode mode,
            @RequestParam(required = false) HackathonOrganizerType organizerType,
            @RequestParam(required = false) BigDecimal minPrizePoolAmount,
            @RequestParam(required = false) BigDecimal maxPrizePoolAmount,
            @RequestParam(required = false) LocalDate startsAfter,
            @RequestParam(required = false) LocalDate startsBefore,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        HackathonSearchCriteria criteria = new HackathonSearchCriteria(
                keyword,
                organizer,
                city,
                region,
                country,
                sourceProvider,
                mode,
                organizerType,
                minPrizePoolAmount,
                maxPrizePoolAmount,
                startsAfter,
                startsBefore,
                active
        );
        return ResponseEntity.ok(hackathonService.search(criteria, page, size));
    }
}

