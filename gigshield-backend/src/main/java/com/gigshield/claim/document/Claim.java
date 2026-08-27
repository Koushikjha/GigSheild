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

    /**
     * When the underlying disruption/cancellation actually happened —
     * distinct from {@code createdAt} (when the claim record was written).
     * Set from the reported cancellation time for ORDER_CANCELLED claims.
     * Used both for audit and, for ORDER_CANCELLED, to distinguish two
     * genuinely different cancellations from an accidental duplicate
     * submission of the same one.
     */
    private LocalDateTime eventOccurredAt;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    @org.springframework.data.annotation.CreatedDate
    private LocalDateTime updatedAt;
}