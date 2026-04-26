// com/gigshield/dashboard/dto/AdminDashboardResponse.java
package com.gigshield.dashboard.dto;

import lombok.*;
import java.util.Map;

@Data @Builder
public class AdminDashboardResponse {
    private long              totalWorkers;
    private long              flaggedClaims;
    private long              approvedClaims;
    private long              rejectedClaims;
    private int               totalPremiumCollectedInr;
    private int               totalPayoutsIssuedInr;
    private int               netPositionInr;
    private Map<String, Long> claimsByStatus;
}