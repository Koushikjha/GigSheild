// com/gigshield/policy/entity/Policy.java
package com.gigshield.policy.entity;

import com.gigshield.policy.enums.PolicyStatus;
import com.gigshield.policy.enums.PolicyTier;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "policies",
        indexes = {
                @Index(name = "idx_policy_user",   columnList = "userId"),
                @Index(name = "idx_policy_active",  columnList = "status, endDate")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyTier tier;

    @Column(nullable = false)
    private Integer premiumPaid;

    @Column(nullable = false)
    private Integer maxPayoutAmount;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status;

    private String razorpayOrderId;
    private String razorpayPaymentId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        status    = PolicyStatus.PENDING_PAYMENT;
    }
}