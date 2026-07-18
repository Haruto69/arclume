package com.arclume.api.controller;

import com.arclume.api.domain.OpportunitySyncStatus;
import org.springframework.http.HttpStatus;

final class OpportunitySyncHttpStatusMapper {

    private OpportunitySyncHttpStatusMapper() {
    }

    static HttpStatus statusFor(OpportunitySyncStatus status) {
        return switch (status) {
            case SUCCEEDED, PARTIAL -> HttpStatus.OK;
            case SKIPPED -> HttpStatus.CONFLICT;
            case FAILED -> HttpStatus.BAD_GATEWAY;
            case RUNNING -> HttpStatus.ACCEPTED;
        };
    }
}
