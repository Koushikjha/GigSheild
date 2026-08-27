// com/gigshield/auth/service/AuthService.java
package com.gigshield.auth.service;

import com.gigshield.auth.dto.AuthResponse;
import com.gigshield.auth.dto.RefreshTokenRequest;
import com.gigshield.auth.dto.SendOtpRequest;
import com.gigshield.auth.dto.VerifyOtpRequest;
import com.gigshield.auth.dto.RegisterRequest;
import com.gigshield.auth.entity.RefreshToken;
import com.gigshield.auth.repository.RefreshTokenRepository;
import com.gigshield.auth.util.JwtUtil;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OtpService           otpService;
    private final JwtUtil              jwtUtil;
    private final UserService          userService;
    private final RefreshTokenRepository refreshTokenRepo;

    // ── Step 1 — send OTP ─────────────────────────────────────────────────────

    @Transactional
    public void sendOtp(SendOtpRequest request) {
        otpService.generateAndSend(request.getPhone());
    }

    // ── Step 2 — verify OTP → issue JWT ──────────────────────────────────────

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        // Verify OTP first — throws if invalid/expired
        otpService.verify(request.getPhone(), request.getOtp());

        // Build registration data from request if provided
        RegisterRequest registrationData = buildRegistrationData(request);

        // Find existing user or create new one
        User user = userService.findOrCreate(
                request.getPhone(), registrationData);

        // Check account not banned
        if (!user.isAccountNonLocked()) {
            throw new IllegalStateException(
                    "Account is permanently suspended");
        }

        return buildTokenPair(user.getPhone(), user.getRole().name());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepo
                .findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepo.save(stored);
            throw new IllegalStateException("Refresh token expired");
        }

        User user = userService.findByPhone(stored.getUsername());
        return buildTokenPair(user.getPhone(), user.getRole().name());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String phone) {
        refreshTokenRepo.revokeAllByUsername(phone);
        log.info("All refresh tokens revoked for user={}", phone);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private AuthResponse buildTokenPair(String phone, String role) {
        String accessToken = jwtUtil.generateAccessToken(
                phone, Map.of("role", role));

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .username(phone)
                .expiresAt(Instant.now()
                        .plusSeconds(7L * 24 * 3600)) // 7 days
                .revoked(false)
                .build();
        refreshTokenRepo.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(86400)
                .build();
    }

    /**
     * Converts VerifyOtpRequest into RegisterRequest.
     * Returns null if no registration fields provided
     * (meaning this is a login, not a registration).
     */
    private RegisterRequest buildRegistrationData(VerifyOtpRequest req) {
        if (req.getFullName() == null
                && req.getCity() == null
                && req.getLatitude() == null) {
            return null;   // login flow — existing user
        }
        RegisterRequest r = new RegisterRequest();
        r.setPhone(req.getPhone());
        r.setFullName(req.getFullName());
        r.setCity(req.getCity());
        r.setLatitude(req.getLatitude());
        r.setLongitude(req.getLongitude());
        r.setPlatform(req.getPlatform());
        return r;
    }
}