// com/gigshield/payment/kafka/PayoutRequestListener.java
package com.gigshield.payment.kafka;

import com.gigshield.kafka.KafkaTopics;
import com.gigshield.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Payment-automation edge of the event-driven pipeline. Consumes
 * {@link PayoutRequestedEvent}s and actually moves money — the payment
 * module never has to be called synchronously by claim processing again.
 *
 * Idempotent by construction: {@link PaymentService#initiateClaimPayout}
 * already no-ops if a PaymentRecord for this claimId exists, so a redelivered
 * message (consumer restart, rebalance) is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutRequestListener {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = KafkaTopics.PAYOUT_REQUESTED,
            groupId = KafkaTopics.GROUP_PAYMENTS,
            containerFactory = "kafkaListenerContainerFactory")
    public void onPayoutRequested(PayoutRequestedEvent event) {
        log.info("Processing payout request claimId={} userId={} amount=₹{} source={}",
                event.getClaimId(), event.getUserId(), event.getAmountInr(), event.getSource());

        paymentService.initiateClaimPayout(
                event.getClaimId(), event.getUserId(), event.getAmountInr());
    }
}
