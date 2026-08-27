// com/gigshield/auth/service/OtpService.java
package com.gigshield.auth.service;

import com.gigshield.auth.entity.OtpRecord;
import com.gigshield.auth.repository.OtpRepository;
import com.gigshield.auth.sms.SmsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final SmsClient     smsClient;

    @Value("${gigshield.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${gigshield.otp.max-attempts:3}")
    private int maxAttempts;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Generate and send ─────────────────────────────────────────────────────

    @Transactional
    public void generateAndSend(String phone) {
        // Invalidate any existing unverified OTPs for this phone
        otpRepository.invalidateAllForPhone(phone);

        String otp = generateOtp();

        OtpRecord record = OtpRecord.builder()
                .phone(phone)
                .otp(otp)
                .expiresAt(LocalDateTime.now()
                        .plusMinutes(otpExpiryMinutes))
                .build();

        otpRepository.save(record);
        smsClient.sendOtp(phone, otp);

        log.info("OTP generated and sent to phone={}", phone);
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    @Transactional
    public void verify(String phone, String otp) {
        OtpRecord record = otpRepository.findLatestValidOtp(phone)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No valid OTP found for this number. " +
                                "Please request a new OTP."));

        // Brute-force guard
        if (record.getAttempts() >= maxAttempts) {
            // Mark as verified (consumed) to prevent further attempts
            record.setVerified(true);
            otpRepository.save(record);
            throw new IllegalStateException(
                    "Too many incorrect attempts. " +
                            "Please request a new OTP.");
        }

        if (!record.getOtp().equals(otp)) {
            record.setAttempts(record.getAttempts() + 1);
            otpRepository.save(record);
            int remaining = maxAttempts - record.getAttempts();
            throw new IllegalArgumentException(
                    "Incorrect OTP. " + remaining + " attempt(s) remaining.");
        }

        // Valid — mark as consumed
        record.setVerified(true);
        otpRepository.save(record);
        log.info("OTP verified successfully for phone={}", phone);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String generateOtp() {
        // 6-digit OTP
        int otp = 100_000 + RANDOM.nextInt(900_000);
        return String.valueOf(otp);
    }
}