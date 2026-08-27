// src/test/java/com/gigshield/claim/kafka/DisruptionEventListenerTest.java
package com.gigshield.claim.kafka;

import com.gigshield.claim.service.ClaimService;
import com.gigshield.event.enums.EventType;
import com.gigshield.event.kafka.DisruptionEventMessage;
import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.enums.PolicyStatus;
import com.gigshield.policy.repository.PolicyRepository;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisruptionEventListenerTest {

    @Mock PolicyRepository policyRepository;
    @Mock ClaimService claimService;
    @Mock UserService userService;
    @Mock DisruptionEventVerificationService verificationService;

    private DisruptionEventListener listener() {
        return new DisruptionEventListener(policyRepository, claimService, userService, verificationService);
    }

    private DisruptionEventMessage message() {
        return DisruptionEventMessage.builder()
                .eventId(1L)
                .eventType(EventType.RAIN)
                .city("Delhi")
                .occurredAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    private Policy activePolicy(Long userId) {
        return Policy.builder().id(10L).userId(userId).city("Delhi")
                .status(PolicyStatus.ACTIVE).build();
    }

    private User userAt(Long id, double lat, double lon) {
        return User.builder().id(id).city("Delhi").latitude(lat).longitude(lon).build();
    }

    @Test
    void onDisruptionEvent_verificationFails_skipsClaimAutomationEntirely() {
        when(policyRepository.findActivePoliciesByCity(eq("Delhi"), any(LocalDate.class)))
                .thenReturn(List.of(activePolicy(1L)));
        when(userService.findById(1L)).thenReturn(userAt(1L, 28.6, 77.2));
        when(verificationService.verify(any(), any(), any(), any(), any()))
                .thenReturn(new DisruptionEventVerdict(false, false, true, "trigger not confirmed"));

        listener().onDisruptionEvent(message());

        verify(claimService, never()).processParametricClaim(any(), any());
    }

    @Test
    void onDisruptionEvent_verificationPasses_proceedsToClaimAutomation() {
        when(policyRepository.findActivePoliciesByCity(eq("Delhi"), any(LocalDate.class)))
                .thenReturn(List.of(activePolicy(1L)));
        when(userService.findById(1L)).thenReturn(userAt(1L, 28.6, 77.2));
        when(verificationService.verify(any(), any(), any(), any(), any()))
                .thenReturn(new DisruptionEventVerdict(true, true, true, "confirmed"));

        listener().onDisruptionEvent(message());

        verify(claimService).processParametricClaim(1L, EventType.RAIN);
    }

    @Test
    void onDisruptionEvent_passesAveragedCoordinatesAndOccurredAt() {
        DisruptionEventMessage msg = message();
        when(policyRepository.findActivePoliciesByCity(eq("Delhi"), any(LocalDate.class)))
                .thenReturn(List.of(activePolicy(1L), activePolicy(2L)));
        when(userService.findById(1L)).thenReturn(userAt(1L, 28.0, 77.0));
        when(userService.findById(2L)).thenReturn(userAt(2L, 29.0, 78.0));
        when(verificationService.verify(any(), any(), any(), any(), any()))
                .thenReturn(new DisruptionEventVerdict(true, true, true, "confirmed"));

        listener().onDisruptionEvent(msg);

        // average of (28.0,77.0) and (29.0,78.0) is (28.5, 77.5)
        verify(verificationService).verify(
                EventType.RAIN, "Delhi", 28.5, 77.5, msg.getOccurredAt());
    }

    @Test
    void onDisruptionEvent_noActivePolicies_neverCallsVerification() {
        when(policyRepository.findActivePoliciesByCity(eq("Delhi"), any(LocalDate.class)))
                .thenReturn(List.of());

        listener().onDisruptionEvent(message());

        verify(verificationService, never()).verify(any(), any(), any(), any(), any());
        verify(claimService, never()).processParametricClaim(any(), any());
    }

    @Test
    void onDisruptionEvent_orderCancelledType_isSkippedDefensively() {
        DisruptionEventMessage msg = DisruptionEventMessage.builder()
                .eventId(2L).eventType(EventType.ORDER_CANCELLED).city("Delhi")
                .occurredAt(LocalDateTime.now())
                .build();

        listener().onDisruptionEvent(msg);

        verify(policyRepository, never()).findActivePoliciesByCity(any(), any());
        verify(verificationService, never()).verify(any(), any(), any(), any(), any());
    }
}
