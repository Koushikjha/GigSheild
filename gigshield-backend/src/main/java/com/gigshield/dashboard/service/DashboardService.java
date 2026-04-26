// com/gigshield/dashboard/service/DashboardService.java
package com.gigshield.dashboard.service;

import com.gigshield.claim.document.Claim;
import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.claim.repository.ClaimRepository;
import com.gigshield.dashboard.dto.AdminDashboardResponse;
import com.gigshield.dashboard.dto.WorkerDashboardResponse;
import com.gigshield.fraud.service.FraudService;
import com.gigshield.policy.service.PolicyService;
import com.gigshield.risk.service.RiskService;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import com.gigshield.payment.repository.PaymentRepository;
import com.gigshield.payment.enums.PaymentType;
import com.gigshield.payment.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserService       userService;
    private final PolicyService     policyService;
    private final ClaimRepository   claimRepository;
    private final RiskService       riskService;
    private final FraudService      fraudService;
    private final PaymentRepository paymentRepository;

    public WorkerDashboardResponse getWorkerDashboard(String phone) {
        User user = userService.findByPhone(phone);

        // Active policy (may be absent)
        var activePolicySafe = safeGetActivePolicy(phone);

        // Last 5 claims
        List<Claim> recentClaims = claimRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 5))
                .getContent();

        // Total paid out
        int totalPaidOut = recentClaims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.PAID
                        || c.getStatus() == ClaimStatus.AUTO_APPROVED
                        || c.getStatus() == ClaimStatus.ADMIN_APPROVED)
                .mapToInt(Claim::getPayoutAmount)
                .sum();

        var riskScore = riskService.getRiskScoreForUser(phone);
        int strikes   = fraudService.getStrikeCount(user.getId());

        return WorkerDashboardResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .city(user.getCity())
                .accountStatus(user.getStatus().name())
                .fraudStrikes(strikes)
                .riskBand(riskScore.getRiskBand())
                .recommendedPremium(riskScore.getRecommendedPremium())
                .activePolicy(activePolicySafe)
                .recentClaimsCount(recentClaims.size())
                .totalPayoutReceived(totalPaidOut)
                .pendingClaimsCount((int) recentClaims.stream()
                        .filter(c -> c.getStatus() == ClaimStatus.FLAGGED_FOR_REVIEW
                                || c.getStatus() == ClaimStatus.PENDING_FRAUD_CHECK)
                        .count())
                .build();
    }

    public AdminDashboardResponse getAdminDashboard() {
        long totalUsers    = userService.countAll();
        long flaggedClaims = claimRepository.findByStatus(ClaimStatus.FLAGGED_FOR_REVIEW).size();
        long approvedClaims= claimRepository.findByStatus(ClaimStatus.AUTO_APPROVED).size()
                + claimRepository.findByStatus(ClaimStatus.ADMIN_APPROVED).size();
        long rejectedClaims= claimRepository.findByStatus(ClaimStatus.ADMIN_REJECTED).size();

        int totalPremiumCollected = paymentRepository
                .findByTypeAndStatus(PaymentType.PREMIUM_COLLECTION, PaymentStatus.SUCCESS)
                .stream().mapToInt(p -> p.getAmountInr()).sum();

        int totalPayoutsIssued = paymentRepository
                .findByTypeAndStatus(PaymentType.CLAIM_PAYOUT, PaymentStatus.SUCCESS)
                .stream().mapToInt(p -> p.getAmountInr()).sum();

        // Claims by status breakdown
        Map<String, Long> claimsByStatus = List.of(ClaimStatus.values()).stream()
                .collect(Collectors.toMap(
                        Enum::name,
                        s -> (long) claimRepository.findByStatus(s).size()
                ));

        return AdminDashboardResponse.builder()
                .totalWorkers(totalUsers)
                .flaggedClaims(flaggedClaims)
                .approvedClaims(approvedClaims)
                .rejectedClaims(rejectedClaims)
                .totalPremiumCollectedInr(totalPremiumCollected)
                .totalPayoutsIssuedInr(totalPayoutsIssued)
                .netPositionInr(totalPremiumCollected - totalPayoutsIssued)
                .claimsByStatus(claimsByStatus)
                .build();
    }

    private com.gigshield.policy.dto.PolicyResponse safeGetActivePolicy(String phone) {
        try {
            return policyService.getActivePolicy(phone);
        } catch (Exception e) {
            return null;
        }
    }
}