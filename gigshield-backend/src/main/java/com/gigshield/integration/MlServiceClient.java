// com/gigshield/integration/MlServiceClient.java
package com.gigshield.integration;

import com.gigshield.config.AppConstants;
import com.gigshield.fraud.dto.FraudCheckRequest;
import com.gigshield.fraud.dto.FraudCheckResponse;
import com.gigshield.integration.dto.TriggerCheckResponse;
import com.gigshield.risk.dto.RiskScoreRequest;
import com.gigshield.risk.dto.RiskScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class MlServiceClient {

    private final RestTemplate restTemplate;

    @Value("${gigshield.ml.base-url}")
    private String mlBaseUrl;

    // ── Risk score ────────────────────────────────────────────────────────────

    public RiskScoreResponse getRiskScore(RiskScoreRequest request) {
        String url = UriComponentsBuilder
                .fromHttpUrl(mlBaseUrl + AppConstants.ML_RISK_SCORE_PATH)
                .queryParam("city",      request.getCity())
                .queryParam("latitude",  request.getLatitude())
                .queryParam("longitude", request.getLongitude())
                .queryParam("platform",  request.getPlatform())
                .toUriString();
        try {
            RiskScoreResponse response =
                    restTemplate.getForObject(url, RiskScoreResponse.class);
            if (response == null) {
                log.warn("ML risk-score returned null, using fallback");
                return buildFallbackRiskScore();
            }
            log.info("ML risk-score: city={} score={} band={} premium={}",
                    request.getCity(),
                    response.getRiskScore(),
                    response.getRiskBand(),
                    response.getRecommendedPremium());
            return response;
        } catch (RestClientException e) {
            log.error("ML risk-score call failed, using fallback: {}",
                    e.getMessage());
            return buildFallbackRiskScore();
        }
    }

    // ── Fraud check ───────────────────────────────────────────────────────────

    public FraudCheckResponse checkFraud(FraudCheckRequest request) {
        String url = mlBaseUrl + AppConstants.ML_FRAUD_CHECK_PATH;
        try {
            FraudCheckResponse response = restTemplate.postForObject(
                    url, request, FraudCheckResponse.class);
            if (response == null) {
                log.warn("ML fraud-check returned null, flagging for review");
                return buildConservativeFraudResponse();
            }
            log.info("ML fraud-check: userId={} score={} rec={}",
                    request.getUserId(),
                    response.getFraudScore(),
                    response.getRecommendation());
            return response;
        } catch (RestClientException e) {
            log.error("ML fraud-check call failed, flagging for review: {}",
                    e.getMessage());
            return buildConservativeFraudResponse();
        }
    }

    // ── Trigger check ─────────────────────────────────────────────────────────

    public TriggerCheckResponse getTriggerCheck(String city) {
        String url = UriComponentsBuilder
                .fromHttpUrl(mlBaseUrl + AppConstants.ML_TRIGGER_CHECK_PATH)
                .queryParam("city", city)
                .toUriString();
        try {
            TriggerCheckResponse response =
                    restTemplate.getForObject(url, TriggerCheckResponse.class);
            if (response == null) {
                log.warn("ML trigger-check returned null for city={}", city);
                return emptyTriggerResponse(city);
            }
            log.info("ML trigger-check: city={} triggers={}",
                    city, response.getActiveTriggers());
            return response;
        } catch (RestClientException e) {
            // Fail safe — never fire a payout on a failed ML call
            log.error("ML trigger-check failed for city={}: {}",
                    city, e.getMessage());
            return emptyTriggerResponse(city);
        }
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    private RiskScoreResponse buildFallbackRiskScore() {
        RiskScoreResponse r = new RiskScoreResponse();
        r.setRiskScore(50.0);
        r.setRecommendedPremium(22);
        r.setRiskBand("MEDIUM");
        return r;
    }

    private FraudCheckResponse buildConservativeFraudResponse() {
        FraudCheckResponse r = new FraudCheckResponse();
        r.setFraudScore(50);
        r.setRecommendation("REVIEW");
        return r;
    }

    private TriggerCheckResponse emptyTriggerResponse(String city) {
        TriggerCheckResponse r = new TriggerCheckResponse();
        r.setCity(city);
        r.setActiveTriggers(Collections.emptyList());
        r.setMetricValues(Collections.emptyMap());
        return r;
    }
}