// com/gigshield/claim/kafka/ClaimStatusSyncListener.java
package com.gigshield.claim.kafka;

import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.claim.repository.ClaimRepository;
import com.gigshield.kafka.KafkaTopics;
import com.gigshield.payment.kafka.PayoutCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Closes the loop on the event-driven pipeline: once the payment module
 * reports a payout as actually completed, the claim it paid out on is
 * updated from AUTO_APPROVED/ADMIN_APPROVED to PAID (or FAILED) — a
 * transition nothing previously performed, since payouts used to be "fire
 * and forget" synchronous calls with no completion signal to react to.
 *
 * Runs in its own consumer group from {@link com.gigshield.payment.kafka.PayoutAuditListener}
 * so a slow claims-DB write never backs up the audit log, and vice versa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimStatusSyncListener {

    private final ClaimRepository claimRepository;

    @KafkaListener(
            topics = KafkaTopics.PAYOUT_COMPLETED,
            groupId = KafkaTopics.GROUP_CLAIM_STATUS_SYNC,
            containerFactory = "kafkaListenerContainerFactory")
    public void onPayoutCompleted(PayoutCompletedEvent event) {
        claimRepository.findById(event.getClaimId()).ifPresentOrElse(claim -> {
            ClaimStatus next = "SUCCESS".equals(event.getStatus()) ? ClaimStatus.PAID : ClaimStatus.FAILED;

            // Idempotent — a redelivered event is a no-op once the claim is already terminal.
            if (claim.getStatus() == ClaimStatus.PAID || claim.getStatus() == ClaimStatus.FAILED) {
                return;
            }

            claim.setStatus(next);
            if (claim.getProcessedAt() == null) {
                claim.setProcessedAt(LocalDateTime.now());
            }
            claimRepository.save(claim);
            log.info("Claim {} synced to status={} from payout completion", claim.getId(), next);
        }, () -> log.warn("Payout-completed event referenced unknown claimId={}", event.getClaimId()));
    }
}
