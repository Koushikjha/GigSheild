// com/gigshield/claim/dto/ClaimResponse.java
package com.gigshield.claim.dto;

import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.event.enums.EventType;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder
public class ClaimResponse {
    private String        id;
    private Long          policyId;
    private EventType     triggerEvent;
    private Integer       payoutAmount;
    private Integer       fraudScore;
    private ClaimStatus   status;
    private String        adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}