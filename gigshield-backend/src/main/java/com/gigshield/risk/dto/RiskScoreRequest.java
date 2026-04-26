// com/gigshield/risk/dto/RiskScoreRequest.java
package com.gigshield.risk.dto;

import lombok.*;

@Data @Builder
public class RiskScoreRequest {
    private String city;
    private Double latitude;
    private Double longitude;
    private String platform;
}