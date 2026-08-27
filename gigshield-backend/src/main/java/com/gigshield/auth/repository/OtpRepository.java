// com/gigshield/auth/repository/OtpRepository.java
package com.gigshield.auth.repository;

import com.gigshield.auth.entity.OtpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpRecord, Long> {

    // Latest unverified, unexpired OTP for this phone
    @Query("SELECT o FROM OtpRecord o WHERE o.phone = :phone " +
            "AND o.verified = false " +
            "AND o.expiresAt > CURRENT_TIMESTAMP " +
            "ORDER BY o.createdAt DESC")
    Optional<OtpRecord> findLatestValidOtp(String phone);

    // Invalidate all previous OTPs when a new one is sent
    @Modifying
    @Query("UPDATE OtpRecord o SET o.verified = true " +
            "WHERE o.phone = :phone AND o.verified = false")
    void invalidateAllForPhone(String phone);
}