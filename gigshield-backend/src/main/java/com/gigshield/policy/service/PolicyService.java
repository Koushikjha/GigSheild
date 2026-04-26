// com/gigshield/policy/service/PolicyService.java
package com.gigshield.policy.service;

import com.gigshield.config.AppConstants;
import com.gigshield.policy.dto.PolicyResponse;
import com.gigshield.policy.dto.PolicyTierInfoResponse;
import com.gigshield.policy.dto.PurchasePolicyRequest;
import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.enums.PolicyStatus;
import com.gigshield.policy.enums.PolicyTier;
import com.gigshield.policy.repository.PolicyRepository;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final UserService      userService;

    // ── Purchase ──────────────────────────────────────────────────────────────

    @Transactional
    public PolicyResponse purchasePolicy(String phone, PurchasePolicyRequest request) {
        User user = userService.findByPhone(phone);

        if (!user.isEnabled()) {
            throw new IllegalStateException(
                    "User account is not eligible for new policies");
        }

        Optional<Policy> existingActive =
                policyRepository.findActivePolicy(user.getId(), LocalDate.now());
        if (existingActive.isPresent()) {
            throw new IllegalStateException(
                    "An active policy already exists for this week");
        }

        PolicyTier tier    = request.getTier();
        int        premium = resolvePremium(tier);

        double income = (request.getWeeklyIncomeEstimate() != null)
                ? request.getWeeklyIncomeEstimate()
                : user.getWeeklyIncomeEstimate();

        int maxPayout = resolveMaxPayout(tier, income);

        Policy policy = Policy.builder()
                .userId(user.getId())
                .city(user.getCity())
                .tier(tier)
                .premiumPaid(premium)
                .maxPayoutAmount(maxPayout)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(
                        AppConstants.POLICY_DURATION_DAYS - 1))
                .build();

        policy = policyRepository.save(policy);
        log.info("Policy created: id={} userId={} tier={} premium=₹{} maxPayout=₹{}",
                policy.getId(), user.getId(), tier, premium, maxPayout);
        return toResponse(policy);
    }

    // ── Tier info (for purchase screen) ──────────────────────────────────────

    public List<PolicyTierInfoResponse> getAllTierInfo(String phone) {
        User   user   = userService.findByPhone(phone);
        double income = user.getWeeklyIncomeEstimate();

        return Arrays.stream(PolicyTier.values())
                .map(tier -> PolicyTierInfoResponse.builder()
                        .tier(tier)
                        .weeklyPremiumInr(resolvePremium(tier))
                        .payoutRatio(resolvePayoutRatio(tier))
                        .estimatedPayoutInr(resolveMaxPayout(tier, income))
                        .description(tierDescription(tier))
                        .build())
                .collect(Collectors.toList());
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public PolicyResponse getActivePolicy(String phone) {
        User user = userService.findByPhone(phone);
        return policyRepository
                .findActivePolicy(user.getId(), LocalDate.now())
                .map(this::toResponse)
                .orElseThrow(() ->
                        new IllegalStateException("No active policy found"));
    }

    public Page<PolicyResponse> getPolicyHistory(String phone, int page) {
        User user = userService.findByPhone(phone);
        return policyRepository
                .findByUserIdOrderByCreatedAtDesc(
                        user.getId(),
                        PageRequest.of(page, AppConstants.DEFAULT_PAGE_SIZE))
                .map(this::toResponse);
    }

    public Policy getActivePolicyEntity(Long userId) {
        return policyRepository
                .findActivePolicy(userId, LocalDate.now())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No active policy for user: " + userId));
    }

    @Transactional
    public void activatePolicy(Long policyId, String razorpayPaymentId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Policy not found: " + policyId));
        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setRazorpayPaymentId(razorpayPaymentId);
        policyRepository.save(policy);
        log.info("Policy {} activated via payment {}", policyId, razorpayPaymentId);
    }

    @Transactional
    public void expireStalePolices() {
        policyRepository.findExpiredPolicies(LocalDate.now())
                .forEach(p -> {
                    p.setStatus(PolicyStatus.EXPIRED);
                    policyRepository.save(p);
                });
    }

    public long countAll() {
        return policyRepository.count();
    }

    // ── Tier resolution helpers ───────────────────────────────────────────────

    public static int resolvePremium(PolicyTier tier) {
        return switch (tier) {
            case STANDARD -> AppConstants.PREMIUM_STANDARD_INR;
            case GOLD     -> AppConstants.PREMIUM_GOLD_INR;
            case PREMIUM  -> AppConstants.PREMIUM_PREMIUM_INR;
        };
    }

    public static double resolvePayoutRatio(PolicyTier tier) {
        return switch (tier) {
            case STANDARD -> AppConstants.PAYOUT_RATIO_STANDARD;
            case GOLD     -> AppConstants.PAYOUT_RATIO_GOLD;
            case PREMIUM  -> AppConstants.PAYOUT_RATIO_PREMIUM;
        };
    }

    public static int resolveMaxPayout(PolicyTier tier, double weeklyIncome) {
        return (int) (weeklyIncome * resolvePayoutRatio(tier));
    }

    private static String tierDescription(PolicyTier tier) {
        return switch (tier) {
            case STANDARD -> "Basic coverage — 30% of weekly income, ₹15/week";
            case GOLD     -> "Enhanced coverage — 35% of weekly income, ₹22/week";
            case PREMIUM  -> "Full coverage — 40% of weekly income, ₹30/week";
        };
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private PolicyResponse toResponse(Policy p) {
        return PolicyResponse.builder()
                .id(p.getId())
                .tier(p.getTier())
                .premiumPaid(p.getPremiumPaid())
                .maxPayoutAmount(p.getMaxPayoutAmount())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .status(p.getStatus())
                .razorpayOrderId(p.getRazorpayOrderId())
                .createdAt(p.getCreatedAt())
                .build();
    }
}