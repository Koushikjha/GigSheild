// src/test/java/com/gigshield/fraud/service/FraudServiceTest.java
package com.gigshield.fraud.service;

import com.gigshield.config.AppConstants;
import com.gigshield.event.enums.EventType;
import com.gigshield.fraud.dto.FraudCheckResponse;
import com.gigshield.fraud.repository.FraudRepository;
import com.gigshield.integration.MlServiceClient;
import com.gigshield.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudServiceTest {

    @Mock MlServiceClient  mlClient;
    @Mock FraudRepository  fraudRepository;
    @Mock UserService      userService;

    @InjectMocks FraudService fraudService;

    @Test
    void evaluate_warEvent_shouldBypassMlAndAutoApprove() {
        when(fraudRepository.existsByClaimId("claim-1")).thenReturn(false);
        when(fraudRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FraudCheckResponse result = fraudService.evaluate(
                1L, "Delhi", 28.6, 77.2,
                EventType.WAR, 10L, "claim-1");

        assertThat(result.getFraudScore()).isZero();
        assertThat(result.getRecommendation()).isEqualTo("AUTO_APPROVE");
        verifyNoInteractions(mlClient);
    }

    @Test
    void evaluate_lowFraudScore_shouldAutoApprove() {
        when(fraudRepository.existsByClaimId("claim-2")).thenReturn(false);
        when(fraudRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FraudCheckResponse mlResponse = new FraudCheckResponse();
        mlResponse.setFraudScore(AppConstants.FRAUD_AUTO_APPROVE_THRESHOLD - 1);
        mlResponse.setRecommendation("AUTO_APPROVE");
        when(mlClient.checkFraud(any())).thenReturn(mlResponse);

        FraudCheckResponse result = fraudService.evaluate(
                1L, "Delhi", 28.6, 77.2,
                EventType.RAIN, 10L, "claim-2");

        assertThat(result.getRecommendation()).isEqualTo("AUTO_APPROVE");
    }

    @Test
    void evaluate_highFraudScore_shouldFlagForReview() {
        when(fraudRepository.existsByClaimId("claim-3")).thenReturn(false);
        when(fraudRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FraudCheckResponse mlResponse = new FraudCheckResponse();
        mlResponse.setFraudScore(AppConstants.FRAUD_AUTO_APPROVE_THRESHOLD + 10);
        mlResponse.setRecommendation("REVIEW");
        when(mlClient.checkFraud(any())).thenReturn(mlResponse);

        FraudCheckResponse result = fraudService.evaluate(
                1L, "Delhi", 28.6, 77.2,
                EventType.AQI, 10L, "claim-3");

        assertThat(result.getRecommendation()).isEqualTo("REVIEW");
    }

    @Test
    void evaluate_duplicateClaim_shouldReturnAutoApproveWithoutMlCall() {
        when(fraudRepository.existsByClaimId("claim-dup")).thenReturn(true);

        FraudCheckResponse result = fraudService.evaluate(
                1L, "Delhi", 28.6, 77.2,
                EventType.RAIN, 10L, "claim-dup");

        assertThat(result.getFraudScore()).isZero();
        assertThat(result.getRecommendation()).isEqualTo("AUTO_APPROVE");
        verifyNoInteractions(mlClient);
    }

    @Test
    void recordFraudStrike_shouldDelegateToUserService() {
        when(fraudRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.emptyList());

        fraudService.recordFraudStrike(1L, "claim-x");

        verify(userService).recordFraudStrike(1L);
    }
}