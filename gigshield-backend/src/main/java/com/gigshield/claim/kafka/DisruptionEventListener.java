// com/gigshield/claim/kafka/DisruptionEventListener.java
package com.gigshield.claim.kafka;

import com.gigshield.claim.service.ClaimService;
import com.gigshield.event.enums.EventType;
import com.gigshield.event.kafka.DisruptionEventMessage;
import com.gigshield.kafka.KafkaTopics;
import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.repository.PolicyRepository;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claims-automation edge of the event-driven pipeline.
 *
 * Consumes {@link DisruptionEventMessage}s from {@link KafkaTopics#DISRUPTION_EVENT_CREATED}
 * and fans each city-wide disruption out into one parametric-claim attempt per
 * worker who currently holds an active policy in that city. This replaces what
 * used to require a manual/synchronous call per worker — the event alone is
 * now enough to (asynchronously) settle every eligible claim in the city.
 *
 * Each worker is processed independently and failures are isolated: one
 * worker's ineligibility (e.g. a duplicate claim, no active policy) never
 * blocks the rest of the batch. A genuinely unexpected failure (DB down, etc.)
 * still propagates so the container's error handler can retry / dead-letter
 * the whole message.
 *
 * Before any of that fan-out happens, {@link DisruptionEventVerificationService}
 * independently corroborates the event against the ML sidecar (trigger-check
 * + risk-score) — real coordinates and real timing, not approximations:
 * coordinates are averaged from the actual affected workers' own registered
 * locations (not a hardcoded city table), and the check runs as of the
 * event's {@code occurredAt} (when it was actually detected/created), not
 * "now" — a Kafka message can sit briefly before a consumer picks it up, and
 * checking "is it disrupted right now" could corroborate (or reject) the
 * event against the wrong moment. Only a "genuine" verdict lets processing
 * continue to publish the event forward into claims automation; per-claim
 * fraud scoring still happens exactly where it always has, inside
 * {@code ClaimService#processParametricClaim}.
 *
 * {@link EventType#ORDER_CANCELLED} is never expected here — it is a single
 * worker's single missed order, not a city-wide event, and is only ever
 * created by {@code ClaimService#reportCancelledOrder} (never published to
 * this topic). The guard below is defense-in-depth in case that invariant
 * is ever broken upstream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisruptionEventListener {

    private final PolicyRepository policyRepository;
    private final ClaimService     claimService;
    private final UserService      userService;
    private final DisruptionEventVerificationService verificationService;

    @KafkaListener(
            topics = KafkaTopics.DISRUPTION_EVENT_CREATED,
            groupId = KafkaTopics.GROUP_CLAIMS_AUTOMATION,
            containerFactory = "kafkaListenerContainerFactory")
    public void onDisruptionEvent(DisruptionEventMessage message) {
        log.info("Claims automation received disruption event id={} type={} city={}",
                message.getEventId(), message.getEventType(), message.getCity());

        if (message.getEventType() == EventType.ORDER_CANCELLED) {
            log.warn("Disruption event id={} has type ORDER_CANCELLED — this should never reach the automated "
                            + "pipeline (it's worker-reported only, see ClaimService#reportCancelledOrder). Skipping.",
                    message.getEventId());
            return;
        }

        List<Policy> activePolicies =
                policyRepository.findActivePoliciesByCity(message.getCity(), LocalDate.now());

        if (activePolicies.isEmpty()) {
            log.info("No active policies in city={} — nothing to auto-claim for event id={}",
                    message.getCity(), message.getEventId());
            return;
        }

        Set<Long> userIds = activePolicies.stream()
                .map(Policy::getUserId)
                .collect(Collectors.toSet());

        double[] coordinates = averageCoordinates(userIds);

        // Event-level genuineness gate — trigger-check + risk-score against
        // the real affected workers' coordinates, as of when the event
        // actually occurred — before this event is allowed to "publish"
        // forward into per-worker claim creation / payout automation.
        DisruptionEventVerdict verdict = verificationService.verify(
                message.getEventType(), message.getCity(),
                coordinates == null ? null : coordinates[0],
                coordinates == null ? null : coordinates[1],
                message.getOccurredAt());
        if (!verdict.genuine()) {
            log.warn("Disruption event id={} type={} city={} failed ML verification ({}) — "
                            + "holding automation, no claims will be auto-created for this event",
                    message.getEventId(), message.getEventType(), message.getCity(), verdict.reason());
            return;
        }
        log.info("Disruption event id={} verified genuine ({}) — proceeding to claims automation",
                message.getEventId(), verdict.reason());

        int created = 0, skipped = 0;
        for (Long userId : userIds) {
            try {
                claimService.processParametricClaim(userId, message.getEventType());
                created++;
            } catch (IllegalStateException e) {
                // Idempotency guard tripped (duplicate claim) or no active policy
                // any more (race with expiry) — expected, not an error.
                log.debug("Skipping auto-claim for userId={} event={}: {}",
                        userId, message.getEventType(), e.getMessage());
                skipped++;
            } catch (Exception e) {
                log.error("Auto-claim failed for userId={} event={} city={}: {}",
                        userId, message.getEventType(), message.getCity(), e.getMessage());
                skipped++;
            }
        }

        log.info("Claims automation done for event id={} city={}: {} created, {} skipped",
                message.getEventId(), message.getCity(), created, skipped);
    }

    /**
     * Averages the real registered coordinates of every affected worker —
     * this replaces what used to be a hardcoded city-centroid lookup table.
     * With several workers spread across a city this is a coarser signal
     * than any one worker's own coordinates (used instead for the
     * ORDER_CANCELLED self-report path, where there's exactly one worker to
     * ask), but it's still the real, actual locations of the people this
     * event would pay out to — not a guess.
     */
    private double[] averageCoordinates(Set<Long> userIds) {
        double latSum = 0, lonSum = 0;
        int counted = 0;
        for (Long userId : userIds) {
            try {
                User user = userService.findById(userId);
                if (user.getLatitude() != null && user.getLongitude() != null) {
                    latSum += user.getLatitude();
                    lonSum += user.getLongitude();
                    counted++;
                }
            } catch (Exception e) {
                log.debug("Could not resolve coordinates for userId={}: {}", userId, e.getMessage());
            }
        }
        if (counted == 0) {
            return null;
        }
        return new double[]{latSum / counted, lonSum / counted};
    }
}
