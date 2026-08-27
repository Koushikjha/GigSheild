// com/gigshield/payment/kafka/PayoutAuditListener.java
package com.gigshield.payment.kafka;

import com.gigshield.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Sample downstream consumer of {@code gigshield.payments.payout-completed}.
 *
 * Stands in for services that don't exist yet — worker notifications (SMS
 * "₹500 credited"), an accounting/ledger export, real-time admin dashboards —
 * all of which can subscribe to this topic independently without the payment
 * module ever needing to know they exist. Runs in its own consumer group so
 * it never competes for partitions with the payment-execution listener.
 */
@Slf4j
@Component
public class PayoutAuditListener {

    @KafkaListener(
            topics = KafkaTopics.PAYOUT_COMPLETED,
            groupId = KafkaTopics.GROUP_AUDIT,
            containerFactory = "kafkaListenerContainerFactory")
    public void onPayoutCompleted(PayoutCompletedEvent event) {
        log.info("[AUDIT] payout claimId={} userId={} amount=₹{} status={} completedAt={}",
                event.getClaimId(), event.getUserId(), event.getAmountInr(),
                event.getStatus(), event.getCompletedAt());
    }
}
