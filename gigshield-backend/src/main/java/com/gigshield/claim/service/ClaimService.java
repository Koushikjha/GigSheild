// com/gigshield/claim/service/ClaimService.java
package com.gigshield.claim.service;

import com.gigshield.claim.document.Claim;
import com.gigshield.claim.dto.AdminReviewRequest;
import com.gigshield.claim.dto.ClaimResponse;
import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.claim.repository.ClaimRepository;
import com.gigshield.config.AppConstants;
import com.gigshield.event.enums.EventType;
import com.gigshield.event.service.EventService;
import com.gigshield.fraud.dto.FraudCheckResponse;
import com.gigshield.fraud.service.FraudService;
import com.gigshield.payment.service.PaymentService;
import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.service.PolicyService;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final UserService     userService;
    private final PolicyService   policyService;
    private final EventService    eventService;
    private final FraudService    fraudService;
    private final PaymentService  paymentService;

    public ClaimResponse processParametricClaim(Long userId, EventType eventType) {
        User   user   = userService.findById(userId);
        Policy policy = policyService.getActivePolicyEntity(userId);

        // Idempotency
        if (claimRepository.existsByUserIdAndPolicyIdAndTriggerEvent(
                userId, policy.getId(), eventType)) {
            throw new IllegalStateException(
                    "Claim already exists for policy=" + policy.getId()
                            + " event=" + eventType);
        }

        // Parametric trigger must be active
        if (!eventService.isTriggerActive(policy.getCity(), eventType)) {
            throw new IllegalStateException(
                    "Parametric trigger not active: city=" + policy.getCity()
                            + " event=" + eventType);
        }

        int payout = calculatePayout(policy, eventType);

        // Pre-save to get a stable claimId before fraud check
        Claim claim = Claim.builder()
                .userId(userId)
                .policyId(policy.getId())
                .triggerEvent(eventType)
                .city(policy.getCity())
                .payoutAmount(payout)
                .status(ClaimStatus.PENDING_FRAUD_CHECK)
                .createdAt(LocalDateTime.now())
                .build();
        claim = claimRepository.save(claim);

        // Fraud evaluation — pass claimId for idempotency in FraudService
        FraudCheckResponse fraud = fraudService.evaluate(
                userId,
                policy.getCity(),
                user.getLatitude(),
                user.getLongitude(),
                eventType,
                policy.getId(),
                claim.getId());          // ← fixed: 6-arg signature

        claim.setFraudScore(fraud.getFraudScore());

        if ("AUTO_APPROVE".equals(fraud.getRecommendation())) {
            claim.setStatus(ClaimStatus.AUTO_APPROVED);
            claim.setProcessedAt(LocalDateTime.now());
            claimRepository.save(claim);
            log.info("Claim {} auto-approved, payout ₹{}", claim.getId(), payout);
            paymentService.initiateClaimPayout(claim.getId(), userId, payout);
        } else {
            claim.setStatus(ClaimStatus.FLAGGED_FOR_REVIEW);
            claimRepository.save(claim);
            log.warn("Claim {} flagged for review (fraudScore={})",
                    claim.getId(), fraud.getFraudScore());
        }

        return toResponse(claim);
    }

    public Page<ClaimResponse> getClaimsForUser(String phone, int page) {
        User user = userService.findByPhone(phone);
        return claimRepository
                .findByUserIdOrderByCreatedAtDesc(
                        user.getId(),
                        PageRequest.of(page, AppConstants.DEFAULT_PAGE_SIZE))
                .map(this::toResponse);
    }

    public List<ClaimResponse> getFlaggedClaims() {
        return claimRepository.findByStatus(ClaimStatus.FLAGGED_FOR_REVIEW)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ClaimResponse adminReview(String claimId, AdminReviewRequest request) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Claim not found: " + claimId));

        if (claim.getStatus() != ClaimStatus.FLAGGED_FOR_REVIEW) {
            throw new IllegalStateException(
                    "Claim is not pending review: " + claimId);
        }

        claim.setAdminNote(request.getAdminNote());
        claim.setProcessedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(request.getApprove())) {
            claim.setStatus(ClaimStatus.ADMIN_APPROVED);
            claimRepository.save(claim);
            paymentService.initiateClaimPayout(
                    claim.getId(), claim.getUserId(), claim.getPayoutAmount());
        } else {
            claim.setStatus(ClaimStatus.ADMIN_REJECTED);
            claimRepository.save(claim);
            // Strike recorded against the user
            fraudService.recordFraudStrike(claim.getUserId(), claimId);
            log.warn("Claim {} rejected — strike issued to userId={}",
                    claimId, claim.getUserId());
        }

        return toResponse(claim);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int calculatePayout(Policy policy, EventType type) {
        return switch (type) {
            case WAR     -> Math.min(policy.getMaxPayoutAmount(),
                    AppConstants.WAR_PAYOUT_CAP_INR);
            case TRAFFIC -> Math.min(policy.getMaxPayoutAmount(),
                    AppConstants.TRAFFIC_PAYOUT_CAP_INR);
            default      -> policy.getMaxPayoutAmount();
        };
    }

    private ClaimResponse toResponse(Claim c) {
        return ClaimResponse.builder()
                .id(c.getId())
                .policyId(c.getPolicyId())
                .triggerEvent(c.getTriggerEvent())
                .payoutAmount(c.getPayoutAmount())
                .fraudScore(c.getFraudScore())
                .status(c.getStatus())
                .adminNote(c.getAdminNote())
                .createdAt(c.getCreatedAt())
                .processedAt(c.getProcessedAt())
                .build();
    }
}