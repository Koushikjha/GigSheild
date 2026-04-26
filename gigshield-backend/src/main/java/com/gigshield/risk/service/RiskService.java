// com/gigshield/risk/service/RiskService.java
package com.gigshield.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gigshield.config.AppConstants;
import com.gigshield.integration.MlServiceClient;
import com.gigshield.risk.dto.RiskScoreRequest;
import com.gigshield.risk.dto.RiskScoreResponse;
import com.gigshield.risk.entity.RiskProfile;
import com.gigshield.risk.repository.RiskRepository;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private final MlServiceClient     mlClient;
    private final UserService         userService;
    private final RiskRepository      riskRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Transactional
    public RiskScoreResponse getRiskScoreForUser(String phone) {
        User   user     = userService.findByPhone(phone);
        String cacheKey = "risk:score:" + phone;

        // ── Redis cache check ─────────────────────────────────────────────────
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                log.debug("Risk score cache hit for user={}", phone);
                return objectMapper.readValue(cached, RiskScoreResponse.class);
            } catch (JsonProcessingException e) {
                // Corrupt cache entry — evict and re-fetch
                log.warn("Corrupt risk score cache for user={}, evicting", phone);
                redisTemplate.delete(cacheKey);
            }
        }

        // ── Call ML sidecar ───────────────────────────────────────────────────
        RiskScoreRequest request = RiskScoreRequest.builder()
                .city(user.getCity())
                .latitude(user.getLatitude())
                .longitude(user.getLongitude())
                .platform(user.getPlatform().name())
                .build();

        RiskScoreResponse response = mlClient.getRiskScore(request);

        // Clamp premium within product bounds
        int clampedPremium = Math.max(AppConstants.PREMIUM_MIN_INR,
                Math.min(AppConstants.PREMIUM_MAX_INR,
                        response.getRecommendedPremium()));
        response.setRecommendedPremium(clampedPremium);

        // ── Persist risk profile ──────────────────────────────────────────────
        upsertRiskProfile(user, response);

        // ── Cache as JSON ─────────────────────────────────────────────────────
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                    cacheKey, json,
                    AppConstants.RISK_SCORE_CACHE_TTL_SEC, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache risk score for user={}: {}",
                    phone, e.getMessage());
        }

        return response;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void upsertRiskProfile(User user, RiskScoreResponse response) {
        RiskProfile profile = riskRepository
                .findByUserId(user.getId())
                .orElse(RiskProfile.builder()
                        .userId(user.getId())
                        .city(user.getCity())
                        .build());

        profile.setLastRiskScore(response.getRiskScore());
        profile.setRecommendedPremium(response.getRecommendedPremium());
        profile.setRiskBand(response.getRiskBand());
        profile.setCity(user.getCity());
        profile.setLastEvaluatedAt(LocalDateTime.now());
        riskRepository.save(profile);
    }
}