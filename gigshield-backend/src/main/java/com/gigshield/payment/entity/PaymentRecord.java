// com/gigshield/payment/entity/PaymentRecord.java
package com.gigshield.payment.entity;

import com.gigshield.payment.enums.PaymentStatus;
import com.gigshield.payment.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records",
        indexes = {
                @Index(name = "idx_payment_user",   columnList = "userId"),
                @Index(name = "idx_payment_policy", columnList = "policyId"),
                @Index(name = "idx_payment_claim",  columnList = "claimId")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long   policyId;
    private String claimId;            // MongoDB claim ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;          // PREMIUM_COLLECTION | CLAIM_PAYOUT

    @Column(nullable = false)
    private Integer amountInr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpayPayoutId;
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}