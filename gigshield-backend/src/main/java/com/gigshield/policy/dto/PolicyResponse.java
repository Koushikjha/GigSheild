// com/gigshield/policy/dto/PolicyResponse.java
package com.gigshield.policy.dto;

import com.gigshield.policy.enums.PolicyStatus;
import com.gigshield.policy.enums.PolicyTier;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class PolicyResponse {
    private Long          id;
    private PolicyTier    tier;
    private Integer       premiumPaid;
    private Integer       maxPayoutAmount;
    private LocalDate     startDate;
    private LocalDate     endDate;
    private PolicyStatus  status;
    private String        razorpayOrderId;
    private LocalDateTime createdAt;
}