// com/gigshield/risk/dto/RiskScoreResponse.java
package com.gigshield.risk.dto;

import lombok.*;

@Data
public class RiskScoreResponse {
    private Double riskScore;           // 0–100
    private Integer recommendedPremium; // ₹
    private String  riskBand;           // LOW | MEDIUM | HIGH
}