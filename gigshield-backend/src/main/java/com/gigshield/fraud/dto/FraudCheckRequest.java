// com/gigshield/fraud/dto/FraudCheckRequest.java
package com.gigshield.fraud.dto;

import lombok.*;

@Data @Builder
public class FraudCheckRequest {
    private Long   userId;
    private String city;
    private Double latitude;
    private Double longitude;
    private String eventType;
    private Long   policyId;
}