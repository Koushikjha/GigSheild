// com/gigshield/kafka/KafkaTopics.java
package com.gigshield.kafka;

/**
 * Central registry of Kafka topic names used across the GigShield event-driven
 * pipeline:
 *
 *   DisruptionEvent created (EventService)
 *        │
 *        ▼
 *   gigshield.events.disruption  ──▶  DisruptionEventListener (claim module)
 *        │                                  │
 *        │                                  ▼ DisruptionEventVerificationService:
 *        │                              ML trigger-check + risk-score must both
 *        │                              corroborate the event as genuine before
 *        │                              it is allowed to fan out further
 *        │                                  │
 *        │                                  ▼ auto-creates a parametric claim
 *        │                              (per-claim ML fraud-check happens here too)
 *        │                              per eligible active policy in the city
 *        │
 *        ▼
 *   ClaimService (AUTO_APPROVED / ADMIN_APPROVED)
 *        │
 *        ▼
 *   gigshield.payments.payout-requested  ──▶  PayoutRequestListener (payment module)
 *        │                                          │
 *        │                                          ▼ executes the payout
 *        ▼
 *   gigshield.payments.payout-completed  ──▶  (audit / notification consumers)
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Published whenever a DisruptionEvent (RAIN/AQI/TRAFFIC/CURFEW/WAR) is created. */
    public static final String DISRUPTION_EVENT_CREATED = "gigshield.events.disruption";

    /** Published when a claim is approved (auto or admin) and money needs to move. */
    public static final String PAYOUT_REQUESTED = "gigshield.payments.payout-requested";

    /** Published after a payout has been executed — downstream audit/notification topic. */
    public static final String PAYOUT_COMPLETED = "gigshield.payments.payout-completed";

    // ── Consumer group ids — one per logical role, even within the same
    //    module, so listener containers never share a group across
    //    different topic subscriptions. ─────────────────────────────────
    public static final String GROUP_CLAIMS_AUTOMATION  = "gigshield-claims-automation";
    public static final String GROUP_CLAIM_STATUS_SYNC  = "gigshield-claim-status-sync";
    public static final String GROUP_PAYMENTS            = "gigshield-payments";
    public static final String GROUP_AUDIT                = "gigshield-audit";
}
