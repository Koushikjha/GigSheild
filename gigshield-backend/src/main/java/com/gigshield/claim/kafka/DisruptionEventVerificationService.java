// com/gigshield/claim/kafka/DisruptionEventVerificationService.java
package com.gigshield.claim.kafka;

import com.gigshield.config.AppConstants;
import com.gigshield.event.enums.EventType;
import com.gigshield.integration.MlServiceClient;
import com.gigshield.integration.dto.TriggerCheckResponse;
import com.gigshield.risk.dto.RiskScoreRequest;
import com.gigshield.risk.dto.RiskScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Gate that sits between "a disruption is claimed to have happened" and
 * "claims automation acts on it" — used by both:
 *
 * <ul>
 *   <li>{@link DisruptionEventListener} — the automated city-wide path
 *       (RAIN/AQI/CURFEW/TRAFFIC/WAR), corroborated against the average of
 *       the actually-affected workers' own registered coordinates; and</li>
 *   <li>{@code ClaimService#reportCancelledOrder} — the user-initiated
 *       ORDER_CANCELLED path, corroborated against that specific worker's
 *       own coordinates.</li>
 * </ul>
 *
 * Earlier this used a small hardcoded table of city-centroid coordinates
 * as a stand-in ("no specific worker to borrow coordinates from" was true
 * for the very first version of this class). That's gone: every caller now
 * has real, registered coordinates of the actual people involved to pass
 * in, so this class no longer looks anything up itself — it just uses
 * whatever {@code latitude}/{@code longitude} it's given.
 *
 * Time matters too: a Kafka message can sit briefly before being consumed,
 * and a worker reports a cancelled order after the fact, sometimes hours
 * later. Checking "is it disrupted right now" would corroborate (or reject)
 * the claim against the wrong moment. {@code occurredAt} carries the actual
 * time to check against; the ML sidecar's {@code /trigger-check} and
 * {@code /risk-score} both support scoring "as of" a past timestamp instead
 * of live/now (see {@code inference/weather.py:get_weather_signal_at}).
 *
 * Two ML calls, mirroring the same "never act on a failed/unconfirmed ML
 * signal" fail-safe philosophy {@link MlServiceClient} already uses
 * elsewhere in this codebase:
 *
 * <ol>
 *   <li><b>Trigger check</b> — confirms, against the ML signal as of
 *       {@code occurredAt}, that this event's type was genuinely active for
 *       the given coordinates at that time.</li>
 *   <li><b>Risk score</b> — an independent "did this actually look like an
 *       elevated-risk situation at that time and place" signal. A claimed
 *       disruption that ML scores as low-risk is a red flag.</li>
 * </ol>
 *
 * Per-claim fraud scoring (the third ML endpoint, {@code POST /fraud-check})
 * still happens exactly where it always has — per user, inside
 * {@code FraudService}, invoked from {@code ClaimService} — because it
 * needs a specific user/policy to evaluate. This class only adds the
 * genuineness gate that sits in front of that.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisruptionEventVerificationService {

    private final MlServiceClient mlServiceClient;

    /**
     * @param eventType the disruption type being corroborated
     * @param city      for logging/ML-response context only — coordinates drive the actual check
     * @param latitude  real registered latitude of the affected worker(s); null skips risk-score corroboration
     * @param longitude real registered longitude of the affected worker(s); null skips risk-score corroboration
     * @param occurredAt the actual moment the disruption/cancellation happened; null means "now"
     */
    public DisruptionEventVerdict verify(EventType eventType, String city,
                                          Double latitude, Double longitude,
                                          LocalDateTime occurredAt) {
        // WAR events bypass ML entirely everywhere else in this codebase
        // (FraudService#evaluate) — mirror that here for consistency rather
        // than gating a war-zone payout on a weather/AQI-oriented risk model.
        if (eventType == EventType.WAR) {
            return new DisruptionEventVerdict(true, true, true,
                    "WAR event — ML verification bypassed per product spec");
        }

        // ── 1) Trigger check ────────────────────────────────────────────────
        TriggerCheckResponse triggerCheck =
                mlServiceClient.getTriggerCheck(city, latitude, longitude, occurredAt);
        boolean triggerConfirmed = triggerCheck.getActiveTriggers() != null
                && triggerCheck.getActiveTriggers().stream()
                        .anyMatch(t -> t.equalsIgnoreCase(eventType.name()));

        // ── 2) Risk score ───────────────────────────────────────────────────
        RiskScoreResponse riskScore = null;
        if (latitude != null && longitude != null) {
            riskScore = mlServiceClient.getRiskScore(RiskScoreRequest.builder()
                    .city(city)
                    .latitude(latitude)
                    .longitude(longitude)
                    .platform("AGGREGATE")
                    .at(occurredAt)
                    .build());
        } else {
            log.warn("No coordinates available for city={} event={} — skipping independent risk-score corroboration",
                    city, eventType);
        }

        boolean riskConfirmed = riskScore != null
                && riskScore.getRiskScore() != null
                && riskScore.getRiskScore() >= AppConstants.EVENT_GENUINE_MIN_RISK_SCORE;

        boolean genuine = triggerConfirmed && riskConfirmed;

        String reason = String.format(Locale.ROOT,
                "occurredAt=%s triggerConfirmed=%s riskConfirmed=%s (riskScore=%s, band=%s)",
                occurredAt != null ? occurredAt : "now",
                triggerConfirmed, riskConfirmed,
                riskScore != null ? riskScore.getRiskScore() : "n/a",
                riskScore != null ? riskScore.getRiskBand() : "n/a");

        return new DisruptionEventVerdict(genuine, triggerConfirmed, riskConfirmed, reason);
    }
}
