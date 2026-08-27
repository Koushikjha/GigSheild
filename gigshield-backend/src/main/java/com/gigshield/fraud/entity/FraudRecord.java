// com/gigshield/fraud/entity/FraudRecord.java
package com.gigshield.fraud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_records",
        indexes = @Index(name = "idx_fraud_user", columnList = "userId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FraudRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String claimId;          // MongoDB claim document ID

    @Column(nullable = false)
    private Integer fraudScore;

    @Column(nullable = false)
    private String recommendation;   // AUTO_APPROVE | REVIEW | REJECT

    private String eventType;

    private boolean strikeIssued;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}