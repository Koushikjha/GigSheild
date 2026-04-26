// com/gigshield/risk/entity/RiskProfile.java
package com.gigshield.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_profiles",
        indexes = @Index(name = "idx_risk_user", columnList = "userId", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private Double lastRiskScore;

    @Column(nullable = false)
    private Integer recommendedPremium;

    @Column(nullable = false)
    private String riskBand;          // LOW | MEDIUM | HIGH

    /** Rolling count of disruptions in user's zone (last 30 days) */
    private Integer disruptionFrequency;

    private LocalDateTime lastEvaluatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}