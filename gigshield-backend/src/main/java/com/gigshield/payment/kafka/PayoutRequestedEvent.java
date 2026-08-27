// com/gigshield/payment/kafka/PayoutRequestedEvent.java
package com.gigshield.payment.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published to {@code gigshield.payments.payout-requested} whenever a claim
 * is approved (auto-approved by the fraud model, or admin-approved on
 * review) and money needs to move. The payment module consumes this
 * asynchronously and stays fully decoupled from claim-decision logic — it
 * only needs to know "who gets paid how much for which claim".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequestedEvent {
    private String         claimId;
    private Long           userId;
    private Integer        amountInr;
    /** AUTO_APPROVED | ADMIN_APPROVED — for audit/observability only. */
    private String         source;
    private LocalDateTime  requestedAt;
}
