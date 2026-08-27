// com/gigshield/dashboard/dto/WorkerDashboardResponse.java
package com.gigshield.dashboard.dto;

import com.gigshield.policy.dto.PolicyResponse;
import lombok.*;

@Data @Builder
public class WorkerDashboardResponse {
    private Long           userId;
    private String         fullName;
    private String         city;
    private String         accountStatus;
    private int            fraudStrikes;
    private String         riskBand;
    private int            recommendedPremium;
    private PolicyResponse activePolicy;
    private int            recentClaimsCount;
    private int            totalPayoutReceived;
    private int            pendingClaimsCount;
}