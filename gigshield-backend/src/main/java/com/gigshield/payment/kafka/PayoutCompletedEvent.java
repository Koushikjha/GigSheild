// com/gigshield/payment/kafka/PayoutCompletedEvent.java
package com.gigshield.payment.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published to {@code gigshield.payments.payout-completed} once a payout has
 * actually been executed (or has failed permanently). This is the extension
 * point for downstream consumers that don't exist yet but naturally plug in
 * here without touching the payment module — e.g. a notification service
 * that SMS's the worker, or an accounting/ledger export.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutCompletedEvent {
    private String         claimId;
    private Long           userId;
    private Integer        amountInr;
    /** SUCCESS | FAILED */
    private String         status;
    private LocalDateTime  completedAt;
}
