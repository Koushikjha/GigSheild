// com/gigshield/claim/document/Claim.java
package com.gigshield.claim.document;

import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.event.enums.EventType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Claim {

    @Id
    private String id;

    @Indexed
    private Long userId;

    @Indexed
    private Long policyId;

    private EventType  triggerEvent;
    private String     city;

    private Integer    payoutAmount;    // ₹
    private Integer    fraudScore;

    @Indexed
    private ClaimStatus status;

    private String  adminNote;
    private String  razorpayPayoutId;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    @org.springframework.data.annotation.CreatedDate
    private LocalDateTime updatedAt;
}