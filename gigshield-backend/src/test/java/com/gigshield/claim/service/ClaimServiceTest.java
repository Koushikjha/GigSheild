// src/test/java/com/gigshield/claim/service/ClaimServiceTest.java
package com.gigshield.claim.service;

import com.gigshield.claim.document.Claim;
import com.gigshield.claim.dto.AdminReviewRequest;
import com.gigshield.claim.dto.ClaimResponse;
import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.claim.repository.ClaimRepository;
import com.gigshield.event.enums.EventType;
import com.gigshield.event.service.EventService;
import com.gigshield.fraud.dto.FraudCheckResponse;
import com.gigshield.fraud.service.FraudService;
import com.gigshield.payment.service.PaymentService;
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
    @Mock FraudService    fraudService;
    @Mock PaymentService  paymentService;

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
        verify(paymentService).initiateClaimPayout("claim-abc", 1L, 1225);
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
        verifyNoInteractions(paymentService);
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
        verify(paymentService).initiateClaimPayout("claim-1", 1L, 1225);
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
        verify(paymentService, never())
                .initiateClaimPayout(any(), any(), any());
    }
}