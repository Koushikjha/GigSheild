// src/test/java/com/gigshield/policy/service/PolicyServiceTest.java
package com.gigshield.policy.service;

import com.gigshield.policy.dto.PolicyResponse;
import com.gigshield.policy.dto.PolicyTierInfoResponse;
import com.gigshield.policy.dto.PurchasePolicyRequest;
import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.enums.PolicyStatus;
import com.gigshield.policy.enums.PolicyTier;
import com.gigshield.policy.repository.PolicyRepository;
import com.gigshield.risk.dto.RiskScoreResponse;
import com.gigshield.risk.service.RiskService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock PolicyRepository policyRepository;
    @Mock UserService      userService;
    @Mock RiskService      riskService;

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

    private RiskScoreResponse riskOf(double score, String band) {
        RiskScoreResponse risk = new RiskScoreResponse();
        risk.setRiskScore(score);
        risk.setRiskBand(band);
        risk.setRecommendedPremium(22);
        return risk;
    }

    // ── purchasePolicy — premium now comes from the place's live risk score ───

    @Test
    void purchasePolicy_standard_atMediumRisk_shouldApplyRiskAdjustedPremium() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyRepository.findActivePolicy(1L, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(riskService.getRiskScoreForUser("9876543210")).thenReturn(riskOf(40, "MEDIUM"));

        Policy saved = Policy.builder()
                .id(10L)
                .userId(1L)
                .city("Delhi")
                .tier(PolicyTier.STANDARD)
                .premiumPaid(15)             // overwritten below by captor assertion
                .maxPayoutAmount(1050)       // 3500 * 0.30
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status(PolicyStatus.PENDING_PAYMENT)
                .build();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        when(policyRepository.save(captor.capture())).thenReturn(saved);

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.STANDARD);

        PolicyResponse response = policyService.purchasePolicy("9876543210", req);

        // base=15, risk=40 -> multiplier = 0.80 + 0.55*0.40 = 1.02 -> 15*1.02=15.3 -> round(15)
        assertThat(captor.getValue().getPremiumPaid()).isEqualTo(15);
        assertThat(response.getTier()).isEqualTo(PolicyTier.STANDARD);
        assertThat(response.getMaxPayoutAmount()).isEqualTo(1050);
        assertThat(response.getStatus()).isEqualTo(PolicyStatus.PENDING_PAYMENT);
    }

    @Test
    void purchasePolicy_gold_atHighRisk_shouldChargeMoreThanBase() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyRepository.findActivePolicy(1L, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(riskService.getRiskScoreForUser("9876543210")).thenReturn(riskOf(80, "HIGH"));

        Policy saved = Policy.builder()
                .id(11L)
                .userId(1L)
                .city("Delhi")
                .tier(PolicyTier.GOLD)
                .premiumPaid(27)
                .maxPayoutAmount(1225)       // 3500 * 0.35
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status(PolicyStatus.PENDING_PAYMENT)
                .build();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        when(policyRepository.save(captor.capture())).thenReturn(saved);

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.GOLD);

        PolicyResponse response = policyService.purchasePolicy("9876543210", req);

        // base=22, risk=80 -> multiplier = 0.80 + 0.55*0.80 = 1.24 -> 22*1.24=27.28 -> round(27)
        assertThat(captor.getValue().getPremiumPaid()).isEqualTo(27);
        assertThat(captor.getValue().getPremiumPaid())
                .isGreaterThan(PolicyService.resolveBasePremium(PolicyTier.GOLD));
        assertThat(response.getMaxPayoutAmount()).isEqualTo(1225);
    }

    @Test
    void purchasePolicy_premium_atLowRisk_shouldChargeLessThanBase() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(policyRepository.findActivePolicy(1L, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(riskService.getRiskScoreForUser("9876543210")).thenReturn(riskOf(0, "LOW"));

        Policy saved = Policy.builder()
                .id(12L)
                .userId(1L)
                .city("Delhi")
                .tier(PolicyTier.PREMIUM)
                .premiumPaid(24)
                .maxPayoutAmount(1400)       // 3500 * 0.40
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status(PolicyStatus.PENDING_PAYMENT)
                .build();

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        when(policyRepository.save(captor.capture())).thenReturn(saved);

        PurchasePolicyRequest req = new PurchasePolicyRequest();
        req.setTier(PolicyTier.PREMIUM);

        PolicyResponse response = policyService.purchasePolicy("9876543210", req);

        // base=30, risk=0 -> multiplier = floor = 0.80 -> 30*0.80=24
        assertThat(captor.getValue().getPremiumPaid()).isEqualTo(24);
        assertThat(captor.getValue().getPremiumPaid())
                .isLessThan(PolicyService.resolveBasePremium(PolicyTier.PREMIUM));
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

        verifyNoInteractions(riskService);
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

        verifyNoInteractions(riskService);
    }

    // ── getAllTierInfo — every tier priced off the same single risk lookup ────

    @Test
    void getAllTierInfo_shouldPriceAllThreeTiersFromSameRiskScore() {
        when(userService.findByPhone("9876543210")).thenReturn(mockUser);
        when(riskService.getRiskScoreForUser("9876543210")).thenReturn(riskOf(100, "HIGH"));

        List<PolicyTierInfoResponse> tiers = policyService.getAllTierInfo("9876543210");

        // risk=100 -> multiplier = ceiling = 1.35
        assertThat(tiers).hasSize(3);
        assertThat(tiers.stream().filter(t -> t.getTier() == PolicyTier.STANDARD).findFirst().orElseThrow()
                .getWeeklyPremiumInr()).isEqualTo(20);  // 15*1.35=20.25 -> 20
        assertThat(tiers.stream().filter(t -> t.getTier() == PolicyTier.GOLD).findFirst().orElseThrow()
                .getWeeklyPremiumInr()).isEqualTo(30);  // 22*1.35=29.7 -> 30
        assertThat(tiers.stream().filter(t -> t.getTier() == PolicyTier.PREMIUM).findFirst().orElseThrow()
                .getWeeklyPremiumInr()).isEqualTo(41);  // 30*1.35=40.5 -> 41

        verify(riskService, times(1)).getRiskScoreForUser("9876543210");
    }

    // ── resolveBasePremium / resolvePremium ────────────────────────────────────

    @Test
    void resolveBasePremium_shouldReturnFlatAmountPerTier() {
        assertThat(PolicyService.resolveBasePremium(PolicyTier.STANDARD)).isEqualTo(15);
        assertThat(PolicyService.resolveBasePremium(PolicyTier.GOLD)).isEqualTo(22);
        assertThat(PolicyService.resolveBasePremium(PolicyTier.PREMIUM)).isEqualTo(30);
    }

    @Test
    void resolvePremium_nullRiskScore_shouldFallBackToBasePremium() {
        assertThat(PolicyService.resolvePremium(PolicyTier.STANDARD, null)).isEqualTo(15);
        assertThat(PolicyService.resolvePremium(PolicyTier.GOLD, null)).isEqualTo(22);
        assertThat(PolicyService.resolvePremium(PolicyTier.PREMIUM, null)).isEqualTo(30);
    }

    @Test
    void resolvePremium_zeroRisk_shouldChargeFloorMultiplier() {
        assertThat(PolicyService.resolvePremium(PolicyTier.STANDARD, 0.0)).isEqualTo(12);  // 15*0.80
        assertThat(PolicyService.resolvePremium(PolicyTier.GOLD, 0.0)).isEqualTo(18);      // 22*0.80=17.6 -> 18
        assertThat(PolicyService.resolvePremium(PolicyTier.PREMIUM, 0.0)).isEqualTo(24);   // 30*0.80
    }

    @Test
    void resolvePremium_maxRisk_shouldChargeCeilingMultiplier() {
        assertThat(PolicyService.resolvePremium(PolicyTier.STANDARD, 100.0)).isEqualTo(20); // 15*1.35=20.25
        assertThat(PolicyService.resolvePremium(PolicyTier.GOLD, 100.0)).isEqualTo(30);     // 22*1.35=29.7
        assertThat(PolicyService.resolvePremium(PolicyTier.PREMIUM, 100.0)).isEqualTo(41);  // 30*1.35=40.5
    }

    @Test
    void resolvePremium_outOfRangeRisk_shouldClampBeforeScaling() {
        assertThat(PolicyService.resolvePremium(PolicyTier.STANDARD, -50.0))
                .isEqualTo(PolicyService.resolvePremium(PolicyTier.STANDARD, 0.0));
        assertThat(PolicyService.resolvePremium(PolicyTier.STANDARD, 500.0))
                .isEqualTo(PolicyService.resolvePremium(PolicyTier.STANDARD, 100.0));
    }

    // ── resolveMaxPayout — unaffected by risk-based pricing ────────────────────

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
