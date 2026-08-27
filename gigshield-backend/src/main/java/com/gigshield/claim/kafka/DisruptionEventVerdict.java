// com/gigshield/claim/kafka/DisruptionEventVerdict.java
package com.gigshield.claim.kafka;

/**
 * Result of independently corroborating a {@code DisruptionEventMessage}
 * against the ML sidecar before claims automation acts on it.
 *
 * @param genuine          overall verdict — {@code true} only when the event
 *                         is safe to fan out into parametric claims / payouts
 * @param triggerConfirmed whether ML's live {@code /trigger-check} still
 *                         lists this event's type as active for the city
 * @param riskConfirmed    whether ML's independent {@code /risk-score} for
 *                         the city corroborates an elevated-risk situation
 * @param reason           human-readable explanation, for logs/audit
 */
public record DisruptionEventVerdict(
        boolean genuine,
        boolean triggerConfirmed,
        boolean riskConfirmed,
        String reason) {
}
