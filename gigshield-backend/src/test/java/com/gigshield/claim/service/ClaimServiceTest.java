// src/test/java/com/gigshield/claim/service/ClaimServiceTest.java
package com.gigshield.claim.service;

import com.gigshield.claim.document.Claim;
import com.gigshield.claim.dto.AdminReviewRequest;
import com.gigshield.claim.dto.ClaimResponse;
import com.gigshield.claim.dto.ReportCancelledOrderRequest;
import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.claim.kafka.DisruptionEventVerdict;
import com.gigshield.claim.kafka.DisruptionEventVerificationService;
import com.gigshield.claim.repository.ClaimRepository;
import com.gigshield.config.AppConstants;
import com.gigshield.event.enums.EventType;
import com.gigshield.event.service.EventService;
import com.gigshield.fraud.dto.FraudCheckResponse;
import com.gigshield.fraud.service.FraudService;
import com.gigshield.payment.kafka.PayoutEventProducer;
import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.enums.PolicyStatus;
import com.gigshield.policy.enums.PolicyTier;
import com.gigshield.policy.service.PolicyService;
import com.gigshield.user.entity.User;
import com.gigshield.user.enums.DeliveryPlatform;
import com.gigshield.user.enums.UserRole;
import com.gigshield.user.enums.UserStatus;
import com.gigshield.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock ClaimRepository claimRepository;
    @Mock UserService     userService;
    @Mock PolicyService   policyService;
    @Mock EventService    eventService;
    @Mock FraudService        fraudService;
    @Mock PayoutEventProducer payoutEventProducer;
    @Mock DisruptionEventVerificationService verificationService;

    @InjectMocks ClaimService claimService;

    private User   mockUser;
    private Policy mockPolicy;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L).phone("9876543210")
                .city("Delhi").latitude(28.6).longitude(77.2)
                .platform(DeliveryPlatform.ZOMATO)
                .role(UserRole.ROLE_WORKER).status(UserStatus.ACTIVE)
                .weeklyIncomeEstimate(3500.0)
                .build();

        mockPolicy = Policy.builder()
                .id(10L).userId(1L).city("Delhi")
                .tier(PolicyTier.GOLD)              // ← tier set
                .premiumPaid(22).maxPayoutAmount(1225)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status(PolicyStatus.ACTIVE)
                .build();
    }

    @Test
    void processParametricClaim_autoApproved_whenLowFraudScore() {
        when(userService.findById(1L)).thenReturn(mockUser);
        when(policyService.getActivePolicyEntity(1L)).thenReturn(mockPolicy);
        when(claimRepository.existsByUserIdAndPolicyIdAndTriggerEvent(
                1L, 10L, EventType.RAIN)).thenReturn(false);
        when(eventService.isTriggerActive("Delhi", EventType.RAIN)).thenReturn(true);

        Claim savedClaim = Claim.builder()
                .id("claim-abc").userId(1L).policyId(10L)
                .triggerEvent(EventType.RAIN).city("Delhi")
                .payoutAmount(1225).status(ClaimStatus.PENDING_FRAUD_CHECK)
                .build();
        when(claimRepository.save(any())).thenReturn(savedClaim);

        FraudCheckResponse fraud = new FraudCheckResponse();
        fraud.setFraudScore(20);
        fraud.setRecommendation("AUTO_APPROVE");
        when(fraudService.evaluate(anyLong(), anyString(), anyDouble(),
                anyDouble(), any(), anyLong(), anyString()))
                .thenReturn(fraud);

        ClaimResponse response =
                claimService.processParametricClaim(1L, EventType.RAIN);

        assertThat(response.getStatus()).isEqualTo(ClaimStatus.AUTO_APPROVED);
        verify(payoutEventProducer).requestPayout("claim-abc", 1L, 1225, "AUTO_APPROVED");
    }

    @Test
    void processParametricClaim_flagged_whenHighFraudScore() {
        when(userService.findById(1L)).thenReturn(mockUser);
        when(policyService.getActivePolicyEntity(1L)).thenReturn(mockPolicy);
        when(claimRepository.existsByUserIdAndPolicyIdAndTriggerEvent(
                any(), any(), any())).thenReturn(false);
        when(eventService.isTriggerActive(any(), any())).thenReturn(true);

        Claim savedClaim = Claim.builder()
                .id("claim-xyz").userId(1L).policyId(10L)
                .triggerEvent(EventType.AQI).city("Delhi")
                .payoutAmount(1225).status(ClaimStatus.PENDING_FRAUD_CHECK)
                .build();
        when(claimRepository.save(any())).thenReturn(savedClaim);

        FraudCheckResponse fraud = new FraudCheckResponse();
        fraud.setFraudScore(75);
        fraud.setRecommendation("REVIEW");
        when(fraudService.evaluate(anyLong(), anyString(), anyDouble(),
                anyDouble(), any(), anyLong(), anyString()))
                .thenReturn(fraud);

        ClaimResponse response =
                claimService.processParametricClaim(1L, EventType.AQI);

        assertThat(response.getStatus()).isEqualTo(ClaimStatus.FLAGGED_FOR_REVIEW);
        verifyNoInteractions(payoutEventProducer);
    }

    @Test
    void processParametricClaim_orderCancelled_shouldBeRejected() {
        assertThatThrownBy(() ->
                claimService.processParametricClaim(1L, EventType.ORDER_CANCELLED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportCancelledOrder");

        verifyNoInteractions(userService, policyService, claimRepository, verificationService);
    }

    @Test
    void adminReview_approve_shouldInitiatePayout() {
        Claim flagged = Claim.builder()
                .id("claim-1").userId(1L).policyId(10L)
                .payoutAmount(1225).status(ClaimStatus.FLAGGED_FOR_REVIEW)
                .build();
        when(claimRepository.findById("claim-1"))
                .thenReturn(Optional.of(flagged));
        when(claimRepository.save(any())).thenReturn(flagged);

        AdminReviewRequest req = new AdminReviewRequest();
        req.setApprove(true);
        req.setAdminNote("Verified");

        ClaimResponse response = claimService.adminReview("claim-1", req);

        assertThat(response.getStatus()).isEqualTo(ClaimStatus.ADMIN_APPROVED);
        verify(payoutEventProducer).requestPayout("claim-1", 1L, 1225, "ADMIN_APPROVED");
        verifyNoInteractions(fraudService);
    }

    @Test
    void adminReview_reject_shouldIssueFraudStrike() {
        Claim flagged = Claim.builder()
                .id("claim-2").userId(1L).policyId(10L)
                .payoutAmount(1225).status(ClaimStatus.FLAGGED_FOR_REVIEW)
                .build();
        when(claimRepository.findById("claim-2"))
                .thenReturn(Optional.of(flagged));
        when(claimRepository.save(any())).thenReturn(flagged);

        AdminReviewRequest req = new AdminReviewRequest();
        req.setApprove(false);
        req.setAdminNote("GPS mismatch");

        claimService.adminReview("claim-2", req);

        verify(fraudService).recordFraudStrike(1L, "claim-2");
        verify(payoutEventProducer, never())
                .requestPayout(any(), any(), anyInt(), any());
    }

    // ── reportCancelledOrder — worker-initiated, not automated ─────────────────

    private ReportCancelledOrderRequest cancelledOrderRequest(LocalDateTime cancelledAt) {
        ReportCancelledOrderRequest req = new ReportCancelledOrderRequest();
        req.setCancelledAt(cancelledAt);
        req.setNote("Restaurant cancelled during heavy rain");
        return req;
    }

    @Test
    void reportCancelledOrder_genuine_lowFraud_shouldAutoApprove() {
        LocalDateTime cancelledAt = LocalDateTime.now().minusHours(1);
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyService.getActivePolicyEntity(1L)).thenReturn(mockPolicy);
        when(claimRepository.existsByUserIdAndPolicyIdAndTriggerEventAndEventOccurredAt(
                1L, 10L, EventType.ORDER_CANCELLED, cancelledAt)).thenReturn(false);
        when(verificationService.verify(eq(EventType.ORDER_CANCELLED), eq("Delhi"),
                eq(28.6), eq(77.2), eq(cancelledAt)))
                .thenReturn(new DisruptionEventVerdict(true, true, true, "confirmed"));

        Claim saved = Claim.builder()
                .id("claim-oc-1").userId(1L).policyId(10L)
                .triggerEvent(EventType.ORDER_CANCELLED).city("Delhi")
                .payoutAmount(150).eventOccurredAt(cancelledAt)
                .status(ClaimStatus.PENDING_FRAUD_CHECK)
                .build();
        when(claimRepository.save(any())).thenReturn(saved);

        FraudCheckResponse fraud = new FraudCheckResponse();
        fraud.setFraudScore(15);
        fraud.setRecommendation("AUTO_APPROVE");
        when(fraudService.evaluate(eq(1L), eq("Delhi"), eq(28.6), eq(77.2),
                eq(EventType.ORDER_CANCELLED), eq(10L), anyString()))
                .thenReturn(fraud);

        ClaimResponse response = claimService.reportCancelledOrder(
                "9876543210", cancelledOrderRequest(cancelledAt));

        assertThat(response.getStatus()).isEqualTo(ClaimStatus.AUTO_APPROVED);
        // capped at ORDER_CANCELLED_PAYOUT_CAP_INR (150), well under the 1225 policy max
        verify(payoutEventProducer).requestPayout("claim-oc-1", 1L,
                AppConstants.ORDER_CANCELLED_PAYOUT_CAP_INR, "AUTO_APPROVED");
    }

    @Test
    void reportCancelledOrder_notGenuine_shouldRejectBeforeAnyFraudCheck() {
        LocalDateTime cancelledAt = LocalDateTime.now().minusHours(1);
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyService.getActivePolicyEntity(1L)).thenReturn(mockPolicy);
        when(claimRepository.existsByUserIdAndPolicyIdAndTriggerEventAndEventOccurredAt(
                any(), any(), any(), any())).thenReturn(false);
        when(verificationService.verify(eq(EventType.ORDER_CANCELLED), eq("Delhi"),
                eq(28.6), eq(77.2), eq(cancelledAt)))
                .thenReturn(new DisruptionEventVerdict(false, false, true, "trigger not confirmed"));

        assertThatThrownBy(() ->
                claimService.reportCancelledOrder("9876543210", cancelledOrderRequest(cancelledAt)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not corroborate");

        verifyNoInteractions(fraudService, payoutEventProducer);
        verify(claimRepository, never()).save(any());
    }

    @Test
    void reportCancelledOrder_futureTimestamp_shouldBeRejected() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyService.getActivePolicyEntity(1L)).thenReturn(mockPolicy);

        LocalDateTime future = LocalDateTime.now().plusHours(2);

        assertThatThrownBy(() ->
                claimService.reportCancelledOrder("9876543210", cancelledOrderRequest(future)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");

        verifyNoInteractions(verificationService, fraudService);
    }

    @Test
    void reportCancelledOrder_tooOld_shouldBeRejected() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyService.getActivePolicyEntity(1L)).thenReturn(mockPolicy);

        LocalDateTime tooOld = LocalDateTime.now()
                .minusHours(AppConstants.ORDER_CANCELLED_REPORT_WINDOW_HOURS + 1);

        assertThatThrownBy(() ->
                claimService.reportCancelledOrder("9876543210", cancelledOrderRequest(tooOld)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 hours");

        verifyNoInteractions(verificationService, fraudService);
    }

    @Test
    void reportCancelledOrder_duplicateReport_shouldBeRejected() {
        LocalDateTime cancelledAt = LocalDateTime.now().minusHours(1);
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyService.getActivePolicyEntity(1L)).thenReturn(mockPolicy);
        when(claimRepository.existsByUserIdAndPolicyIdAndTriggerEventAndEventOccurredAt(
                1L, 10L, EventType.ORDER_CANCELLED, cancelledAt)).thenReturn(true);

        assertThatThrownBy(() ->
                claimService.reportCancelledOrder("9876543210", cancelledOrderRequest(cancelledAt)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been reported");

        verifyNoInteractions(verificationService, fraudService);
    }
}
