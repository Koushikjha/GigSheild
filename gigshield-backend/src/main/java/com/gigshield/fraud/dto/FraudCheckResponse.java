// com/gigshield/fraud/dto/FraudCheckResponse.java
package com.gigshield.fraud.dto;

import lombok.*;

@Data
public class FraudCheckResponse {
    private Integer fraudScore;       // 0–100
    private String  recommendation;   // AUTO_APPROVE | REVIEW | REJECT
}