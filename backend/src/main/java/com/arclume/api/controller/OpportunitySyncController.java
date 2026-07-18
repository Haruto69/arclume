package com.arclume.api.controller;

import com.arclume.api.dto.OpportunitySyncErrorResponse;
import com.arclume.api.dto.OpportunitySyncResponse;
import com.arclume.api.service.opportunity.OpportunitySyncService;
import com.arclume.api.service.opportunity.UnknownOpportunityProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/opportunity-sync")
public class OpportunitySyncController {

    private final OpportunitySyncService opportunitySyncService;

    public OpportunitySyncController(OpportunitySyncService opportunitySyncService) {
        this.opportunitySyncService = opportunitySyncService;
    }

    @PostMapping("/{providerKey}")
    public ResponseEntity<?> sync(@PathVariable String providerKey) {
        try {
            OpportunitySyncResponse response = opportunitySyncService.sync(providerKey);
            return ResponseEntity.status(OpportunitySyncHttpStatusMapper.statusFor(response.status())).body(response);
        } catch (UnknownOpportunityProviderException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new OpportunitySyncErrorResponse("UNKNOWN_PROVIDER", e.getMessage()));
        }
    }

}
