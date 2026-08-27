// com/gigshield/fraud/service/FraudService.java
package com.gigshield.fraud.service;

import com.gigshield.config.AppConstants;
import com.gigshield.event.enums.EventType;
import com.gigshield.fraud.dto.FraudCheckRequest;
import com.gigshield.fraud.dto.FraudCheckResponse;
import com.gigshield.fraud.entity.FraudRecord;
import com.gigshield.fraud.repository.FraudRepository;
import com.gigshield.integration.MlServiceClient;
import com.gigshield.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudService {

    private final MlServiceClient  mlClient;
    private final FraudRepository  fraudRepository;
    private final UserService      userService;

    /**
     * Evaluates fraud for a parametric claim.
     * WAR events bypass ML — flat approval per product spec.
     */
    @Transactional
    public FraudCheckResponse evaluate(Long userId, String city,
                                       Double lat, Double lon,
                                       EventType eventType, Long policyId,
                                       String claimId) {
        // Idempotency guard
        if (fraudRepository.existsByClaimId(claimId)) {
            log.warn("Duplicate fraud check attempted for claimId={}", claimId);
            FraudCheckResponse dup = new FraudCheckResponse();
            dup.setFraudScore(0);
            dup.setRecommendation("AUTO_APPROVE");
            return dup;
        }

        FraudCheckResponse response;

        if (eventType == EventType.WAR) {
            log.info("WAR event — ML fraud model suspended for userId={}", userId);
            response = new FraudCheckResponse();
            response.setFraudScore(0);
            response.setRecommendation("AUTO_APPROVE");
        } else {
            FraudCheckRequest request = FraudCheckRequest.builder()
                    .userId(userId)
                    .city(city)
                    .latitude(lat)
                    .longitude(lon)
                    .eventType(eventType.name())
                    .policyId(policyId)
                    .build();

            response = mlClient.checkFraud(request);

            // Single source of truth for threshold decision
            if (response.getFraudScore() < AppConstants.FRAUD_AUTO_APPROVE_THRESHOLD) {
                response.setRecommendation("AUTO_APPROVE");
            } else {
                response.setRecommendation("REVIEW");
            }
        }

        // Persist audit record
        FraudRecord record = FraudRecord.builder()
                .userId(userId)
                .claimId(claimId)
                .fraudScore(response.getFraudScore())
                .recommendation(response.getRecommendation())
                .eventType(eventType.name())
                .strikeIssued(false)
                .build();
        fraudRepository.save(record);

        log.info("Fraud eval: userId={}, event={}, score={}, rec={}",
                userId, eventType, response.getFraudScore(), response.getRecommendation());
        return response;
    }

    /**
     * Called after admin rejects a claim — issues strike and auto-bans at threshold.
     */
    @Transactional
    public void recordFraudStrike(Long userId, String claimId) {
        fraudRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> r.getClaimId().equals(claimId) && !r.isStrikeIssued())
                .findFirst()
                .ifPresent(r -> {
                    r.setStrikeIssued(true);
                    fraudRepository.save(r);
                });

        userService.recordFraudStrike(userId);
        log.warn("Fraud strike issued to userId={} for claimId={}", userId, claimId);
    }

    public int getStrikeCount(Long userId) {
        return fraudRepository.countStrikesByUserId(userId);
    }
}