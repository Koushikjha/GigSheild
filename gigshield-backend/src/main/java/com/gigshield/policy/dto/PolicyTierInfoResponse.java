// com/gigshield/policy/dto/PolicyTierInfoResponse.java
package com.gigshield.policy.dto;

import com.gigshield.policy.enums.PolicyTier;
import lombok.*;

@Data @Builder
public class PolicyTierInfoResponse {
    private PolicyTier tier;
    private int        weeklyPremiumInr;
    private double     payoutRatio;
    private int        estimatedPayoutInr;   // calculated against user's income
    private String     description;
}