// com/gigshield/policy/dto/PurchasePolicyRequest.java
package com.gigshield.policy.dto;

import com.gigshield.policy.enums.PolicyTier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PurchasePolicyRequest {

    @NotNull(message = "Policy tier is required: STANDARD, GOLD, or PREMIUM")
    private PolicyTier tier;

    @Positive
    private Double weeklyIncomeEstimate;   // optional — overrides profile value
}