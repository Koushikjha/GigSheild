// com/gigshield/claim/service/ClaimService.java
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
    private final EventService        eventService;
    private final FraudService        fraudService;
    private final PayoutEventProducer payoutEventProducer;
    private final DisruptionEventVerificationService verificationService;

    public ClaimResponse processParametricClaim(Long userId, EventType eventType) {
        if (eventType == EventType.ORDER_CANCELLED) {
            // Defense-in-depth: this path is for the automated city-wide
            // pipeline only. ORDER_CANCELLED claims are worker-reported —
            // see reportCancelledOrder() and EventType's javadoc for why.
            throw new IllegalArgumentException(
                    "ORDER_CANCELLED claims must be reported by the worker via reportCancelledOrder(), "
                            + "not processed automatically");
        }

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
            payoutEventProducer.requestPayout(claim.getId(), userId, payout, "AUTO_APPROVED");
        } else {
            claim.setStatus(ClaimStatus.FLAGGED_FOR_REVIEW);
            claimRepository.save(claim);
            log.warn("Claim {} flagged for review (fraudScore={})",
                    claim.getId(), fraud.getFraudScore());
        }

        return toResponse(claim);
    }

    /**
     * The ONLY way an ORDER_CANCELLED claim gets created — see
     * EventType#ORDER_CANCELLED and this class's package docs for why it's
     * worker-initiated rather than automated.
     *
     * Genuineness is checked against the reported cancellation time, not
     * "now": {@code request.getCancelledAt()} is passed straight through to
     * {@link DisruptionEventVerificationService}, which asks the ML sidecar
     * to score trigger/risk as of that exact moment using this worker's own
     * real registered coordinates — not a city-wide approximation, since
     * there's exactly one worker involved here.
     */
    public ClaimResponse reportCancelledOrder(String phone, ReportCancelledOrderRequest request) {
        User   user   = userService.findByPhone(phone);
        Policy policy = policyService.getActivePolicyEntity(user.getId());

        LocalDateTime cancelledAt = request.getCancelledAt();
        LocalDateTime now = LocalDateTime.now();

        if (cancelledAt.isAfter(now.plusMinutes(AppConstants.ORDER_CANCELLED_FUTURE_TOLERANCE_MINUTES))) {
            throw new IllegalArgumentException("cancelledAt cannot be in the future");
        }
        if (cancelledAt.isBefore(now.minusHours(AppConstants.ORDER_CANCELLED_REPORT_WINDOW_HOURS))) {
            throw new IllegalArgumentException(
                    "Cancelled orders must be reported within "
                            + AppConstants.ORDER_CANCELLED_REPORT_WINDOW_HOURS + " hours of happening");
        }

        // Dedup: block resubmitting the exact same cancellation, not
        // multiple genuinely different ones (see ClaimRepository).
        if (claimRepository.existsByUserIdAndPolicyIdAndTriggerEventAndEventOccurredAt(
                user.getId(), policy.getId(), EventType.ORDER_CANCELLED, cancelledAt)) {
            throw new IllegalStateException("This cancellation has already been reported");
        }

        // Genuineness gate — evaluated AS OF cancelledAt, using this
        // worker's own real coordinates. This is the hard gate that keeps
        // the self-report flow from being a rubber stamp: a claim only
        // proceeds to fraud-scoring/payout if ML corroborates that a real
        // trigger-worthy disruption was actually happening, at that place,
        // at that time.
        DisruptionEventVerdict verdict = verificationService.verify(
                EventType.ORDER_CANCELLED, policy.getCity(),
                user.getLatitude(), user.getLongitude(), cancelledAt);
        if (!verdict.genuine()) {
            throw new IllegalStateException(
                    "Could not corroborate a genuine disruption at the reported time/location: " + verdict.reason());
        }

        int payout = calculatePayout(policy, EventType.ORDER_CANCELLED);

        Claim claim = Claim.builder()
                .userId(user.getId())
                .policyId(policy.getId())
                .triggerEvent(EventType.ORDER_CANCELLED)
                .city(policy.getCity())
                .payoutAmount(payout)
                .eventOccurredAt(cancelledAt)
                .adminNote(request.getNote())
                .status(ClaimStatus.PENDING_FRAUD_CHECK)
                .createdAt(now)
                .build();
        claim = claimRepository.save(claim);

        // Same fraud evaluation the automated path uses — per-claim, per-user.
        FraudCheckResponse fraud = fraudService.evaluate(
                user.getId(), policy.getCity(), user.getLatitude(), user.getLongitude(),
                EventType.ORDER_CANCELLED, policy.getId(), claim.getId());
        claim.setFraudScore(fraud.getFraudScore());

        if ("AUTO_APPROVE".equals(fraud.getRecommendation())) {
            claim.setStatus(ClaimStatus.AUTO_APPROVED);
            claim.setProcessedAt(LocalDateTime.now());
            claimRepository.save(claim);
            log.info("Cancelled-order claim {} auto-approved, payout ₹{}", claim.getId(), payout);
            payoutEventProducer.requestPayout(claim.getId(), user.getId(), payout, "AUTO_APPROVED");
        } else {
            claim.setStatus(ClaimStatus.FLAGGED_FOR_REVIEW);
            claimRepository.save(claim);
            log.warn("Cancelled-order claim {} flagged for review (fraudScore={})",
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
            payoutEventProducer.requestPayout(
                    claim.getId(), claim.getUserId(), claim.getPayoutAmount(), "ADMIN_APPROVED");
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
            case WAR             -> Math.min(policy.getMaxPayoutAmount(),
                    AppConstants.WAR_PAYOUT_CAP_INR);
            case TRAFFIC         -> Math.min(policy.getMaxPayoutAmount(),
                    AppConstants.TRAFFIC_PAYOUT_CAP_INR);
            case ORDER_CANCELLED -> Math.min(policy.getMaxPayoutAmount(),
                    AppConstants.ORDER_CANCELLED_PAYOUT_CAP_INR);
            default              -> policy.getMaxPayoutAmount();
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
                .eventOccurredAt(c.getEventOccurredAt())
                .createdAt(c.getCreatedAt())
                .processedAt(c.getProcessedAt())
                .build();
    }
}