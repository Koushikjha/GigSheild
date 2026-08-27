// com/gigshield/risk/dto/RiskScoreRequest.java
package com.gigshield.risk.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data @Builder
public class RiskScoreRequest {
    private String city;
    private Double latitude;
    private Double longitude;
    private String platform;

    /**
     * When set, the ML sidecar scores the place as of this moment (via its
     * recent-history window) instead of live/now — used to corroborate a
     * specific past event rather than whatever conditions happen to be at
     * request time. Null keeps the original "live" behavior.
     */
    private LocalDateTime at;
}