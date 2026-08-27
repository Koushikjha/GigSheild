// src/test/java/com/gigshield/claim/kafka/DisruptionEventVerificationServiceTest.java
package com.gigshield.claim.kafka;

import com.gigshield.config.AppConstants;
import com.gigshield.event.enums.EventType;
import com.gigshield.integration.MlServiceClient;
import com.gigshield.integration.dto.TriggerCheckResponse;
import com.gigshield.risk.dto.RiskScoreResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisruptionEventVerificationServiceTest {

    @Mock MlServiceClient mlServiceClient;

    private DisruptionEventVerificationService service() {
        return new DisruptionEventVerificationService(mlServiceClient);
    }

    @Test
    void verify_warEvent_bypassesMlAndIsGenuine() {
        DisruptionEventVerdict verdict = service().verify(
                EventType.WAR, "Delhi", 28.6, 77.2, LocalDateTime.now());

        assertThat(verdict.genuine()).isTrue();
        verifyNoInteractions(mlServiceClient);
    }

    @Test
    void verify_triggerConfirmedAndRiskConfirmed_isGenuine() {
        LocalDateTime occurredAt = LocalDateTime.now().minusMinutes(30);

        TriggerCheckResponse trigger = new TriggerCheckResponse();
        trigger.setCity("Delhi");
        trigger.setActiveTriggers(List.of("RAIN"));
        when(mlServiceClient.getTriggerCheck("Delhi", 28.6, 77.2, occurredAt)).thenReturn(trigger);

        RiskScoreResponse risk = new RiskScoreResponse();
        risk.setRiskScore(AppConstants.EVENT_GENUINE_MIN_RISK_SCORE + 10);
        risk.setRiskBand("MEDIUM");
        when(mlServiceClient.getRiskScore(any())).thenReturn(risk);

        DisruptionEventVerdict verdict = service().verify(
                EventType.RAIN, "Delhi", 28.6, 77.2, occurredAt);

        assertThat(verdict.genuine()).isTrue();
        assertThat(verdict.triggerConfirmed()).isTrue();
        assertThat(verdict.riskConfirmed()).isTrue();
    }

    @Test
    void verify_triggerNotConfirmed_isNotGenuine() {
        LocalDateTime occurredAt = LocalDateTime.now();

        TriggerCheckResponse trigger = new TriggerCheckResponse();
        trigger.setCity("Delhi");
        trigger.setActiveTriggers(List.of());
        when(mlServiceClient.getTriggerCheck("Delhi", 28.6, 77.2, occurredAt)).thenReturn(trigger);

        RiskScoreResponse risk = new RiskScoreResponse();
        risk.setRiskScore(AppConstants.EVENT_GENUINE_MIN_RISK_SCORE + 10);
        risk.setRiskBand("MEDIUM");
        when(mlServiceClient.getRiskScore(any())).thenReturn(risk);

        DisruptionEventVerdict verdict = service().verify(
                EventType.RAIN, "Delhi", 28.6, 77.2, occurredAt);

        assertThat(verdict.genuine()).isFalse();
        assertThat(verdict.triggerConfirmed()).isFalse();
    }

    @Test
    void verify_riskScoreBelowFloor_isNotGenuine() {
        LocalDateTime occurredAt = LocalDateTime.now();

        TriggerCheckResponse trigger = new TriggerCheckResponse();
        trigger.setCity("Delhi");
        trigger.setActiveTriggers(List.of("RAIN"));
        when(mlServiceClient.getTriggerCheck("Delhi", 28.6, 77.2, occurredAt)).thenReturn(trigger);

        RiskScoreResponse risk = new RiskScoreResponse();
        risk.setRiskScore(AppConstants.EVENT_GENUINE_MIN_RISK_SCORE - 5);
        risk.setRiskBand("LOW");
        when(mlServiceClient.getRiskScore(any())).thenReturn(risk);

        DisruptionEventVerdict verdict = service().verify(
                EventType.RAIN, "Delhi", 28.6, 77.2, occurredAt);

        assertThat(verdict.genuine()).isFalse();
        assertThat(verdict.riskConfirmed()).isFalse();
    }

    @Test
    void verify_noCoordinates_skipsRiskScoreCallAndIsNotGenuine() {
        LocalDateTime occurredAt = LocalDateTime.now();

        TriggerCheckResponse trigger = new TriggerCheckResponse();
        trigger.setCity("Delhi");
        trigger.setActiveTriggers(List.of("RAIN"));
        when(mlServiceClient.getTriggerCheck(eq("Delhi"), isNull(), isNull(), eq(occurredAt))).thenReturn(trigger);

        DisruptionEventVerdict verdict = service().verify(
                EventType.RAIN, "Delhi", null, null, occurredAt);

        assertThat(verdict.riskConfirmed()).isFalse();
        assertThat(verdict.genuine()).isFalse();
    }

    @Test
    void verify_passesOccurredAtThrough_notNow() {
        LocalDateTime occurredAt = LocalDateTime.now().minusHours(5);

        TriggerCheckResponse trigger = new TriggerCheckResponse();
        trigger.setCity("Delhi");
        trigger.setActiveTriggers(List.of("RAIN"));
        when(mlServiceClient.getTriggerCheck("Delhi", 28.6, 77.2, occurredAt)).thenReturn(trigger);

        RiskScoreResponse risk = new RiskScoreResponse();
        risk.setRiskScore(80.0);
        risk.setRiskBand("HIGH");
        when(mlServiceClient.getRiskScore(argThat(req -> occurredAt.equals(req.getAt())))).thenReturn(risk);

        DisruptionEventVerdict verdict = service().verify(
                EventType.RAIN, "Delhi", 28.6, 77.2, occurredAt);

        assertThat(verdict.genuine()).isTrue();
    }
}
