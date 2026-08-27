// com/gigshield/payment/kafka/PayoutEventProducer.java
package com.gigshield.payment.kafka;

import com.gigshield.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Called by ClaimService once a claim is approved (auto or admin). */
    public void requestPayout(String claimId, Long userId, int amountInr, String source) {
        PayoutRequestedEvent event = PayoutRequestedEvent.builder()
                .claimId(claimId)
                .userId(userId)
                .amountInr(amountInr)
                .source(source)
                .requestedAt(LocalDateTime.now())
                .build();

        // Keyed by userId so a worker's payouts are processed in order.
        kafkaTemplate.send(KafkaTopics.PAYOUT_REQUESTED, String.valueOf(userId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payout-requested for claimId={}: {}",
                                claimId, ex.getMessage());
                    } else {
                        log.info("Published payout-requested claimId={} userId={} amount=₹{} source={}",
                                claimId, userId, amountInr, source);
                    }
                });
    }

    /** Called by PaymentService once the payout has actually been executed. */
    public void publishCompleted(String claimId, Long userId, int amountInr, String status) {
        PayoutCompletedEvent event = PayoutCompletedEvent.builder()
                .claimId(claimId)
                .userId(userId)
                .amountInr(amountInr)
                .status(status)
                .completedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(KafkaTopics.PAYOUT_COMPLETED, String.valueOf(userId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payout-completed for claimId={}: {}",
                                claimId, ex.getMessage());
                    }
                });
    }
}
