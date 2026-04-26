// src/test/java/com/gigshield/policy/service/PolicyServiceTest.java
package com.gigshield.policy.service;

import com.gigshield.policy.dto.PolicyResponse;
import com.gigshield.policy.dto.PurchasePolicyRequest;
import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.enums.PolicyStatus;
import com.gigshield.policy.enums.PolicyTier;
import com.gigshield.policy.repository.PolicyRepository;
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
class PolicyServiceTest {

    @Mock PolicyRepository policyRepository;
    @Mock UserService      userService;

    // RiskService mock removed — no longer injected into PolicyService
    @InjectMocks PolicyService policyService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .phone("9876543210")
                .city("Delhi")
                .platform(DeliveryPlatform.ZOMATO)
                .role(UserRole.ROLE_WORKER)
                .status(UserStatus.ACTIVE)
                .weeklyIncomeEstimate(3500.0)
                .latitude(28.6)
                .longitude(77.2)
                .build();
    }

    @Test
    void purchasePolicy_standard_shouldCreateCorrectPremiumAndPayout() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyRepository.findActivePolicy(1L, LocalDate.now()))
                .thenReturn(Optional.empty());

        Policy saved = Policy.builder()
                .id(10L)
                .userId(1L)
                .city("Delhi")
                .tier(PolicyTier.STANDARD)          // ← tier set
                .premiumPaid(15)
                .maxPayoutAmount(1050)              // 3500 * 0.30
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status(PolicyStatus.PENDING_PAYMENT)
                .build();
        when(policyRepository.save(any())).thenReturn(saved);

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.STANDARD);

        PolicyResponse response = policyService.purchasePolicy("9876543210", req);

        assertThat(response.getTier()).isEqualTo(PolicyTier.STANDARD);
        assertThat(response.getPremiumPaid()).isEqualTo(15);
        assertThat(response.getMaxPayoutAmount()).isEqualTo(1050);
        assertThat(response.getStatus()).isEqualTo(PolicyStatus.PENDING_PAYMENT);
    }

    @Test
    void purchasePolicy_gold_shouldApplyCorrectRatio() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyRepository.findActivePolicy(1L, LocalDate.now()))
                .thenReturn(Optional.empty());

        Policy saved = Policy.builder()
                .id(11L)
                .userId(1L)
                .city("Delhi")
                .tier(PolicyTier.GOLD)              // ← tier set
                .premiumPaid(22)
                .maxPayoutAmount(1225)              // 3500 * 0.35
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status(PolicyStatus.PENDING_PAYMENT)
                .build();
        when(policyRepository.save(any())).thenReturn(saved);

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.GOLD);

        PolicyResponse response = policyService.purchasePolicy("9876543210", req);

        assertThat(response.getTier()).isEqualTo(PolicyTier.GOLD);
        assertThat(response.getPremiumPaid()).isEqualTo(22);
        assertThat(response.getMaxPayoutAmount()).isEqualTo(1225);
    }

    @Test
    void purchasePolicy_premium_shouldApplyCorrectRatio() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyRepository.findActivePolicy(1L, LocalDate.now()))
                .thenReturn(Optional.empty());

        Policy saved = Policy.builder()
                .id(12L)
                .userId(1L)
                .city("Delhi")
                .tier(PolicyTier.PREMIUM)           // ← tier set
                .premiumPaid(30)
                .maxPayoutAmount(1400)              // 3500 * 0.40
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status(PolicyStatus.PENDING_PAYMENT)
                .build();
        when(policyRepository.save(any())).thenReturn(saved);

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.PREMIUM);

        PolicyResponse response = policyService.purchasePolicy("9876543210", req);

        assertThat(response.getTier()).isEqualTo(PolicyTier.PREMIUM);
        assertThat(response.getPremiumPaid()).isEqualTo(30);
        assertThat(response.getMaxPayoutAmount()).isEqualTo(1400);
    }

    @Test
    void purchasePolicy_shouldThrow_whenActivePolicyExists() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyRepository.findActivePolicy(1L, LocalDate.now()))
                .thenReturn(Optional.of(new Policy()));

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.STANDARD);

        assertThatThrownBy(() ->
                policyService.purchasePolicy("9876543210", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active policy");
    }

    @Test
    void purchasePolicy_shouldThrow_whenUserBanned() {
        mockUser.setStatus(UserStatus.BANNED);
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.GOLD);

        assertThatThrownBy(() ->
                policyService.purchasePolicy("9876543210", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    void resolvePremium_shouldReturnCorrectAmountPerTier() {
        assertThat(PolicyService.resolvePremium(PolicyTier.STANDARD)).isEqualTo(15);
        assertThat(PolicyService.resolvePremium(PolicyTier.GOLD)).isEqualTo(22);
        assertThat(PolicyService.resolvePremium(PolicyTier.PREMIUM)).isEqualTo(30);
    }

    @Test
    void resolveMaxPayout_shouldCalculateCorrectlyPerTier() {
        double income = 3500.0;
        assertThat(PolicyService.resolveMaxPayout(PolicyTier.STANDARD, income))
                .isEqualTo(1050);   // 3500 * 0.30
        assertThat(PolicyService.resolveMaxPayout(PolicyTier.GOLD, income))
                .isEqualTo(1225);   // 3500 * 0.35
        assertThat(PolicyService.resolveMaxPayout(PolicyTier.PREMIUM, income))
                .isEqualTo(1400);   // 3500 * 0.40
    }
}