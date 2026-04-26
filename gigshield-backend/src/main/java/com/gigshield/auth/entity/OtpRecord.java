// com/gigshield/auth/entity/OtpRecord.java
package com.gigshield.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_records",
        indexes = @Index(name = "idx_otp_phone", columnList = "phone"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(nullable = false, length = 6)
    private String otp;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified;

    // Brute-force guard — max 3 wrong attempts before record is invalidated
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        verified  = false;
        attempts  = 0;
    }
}