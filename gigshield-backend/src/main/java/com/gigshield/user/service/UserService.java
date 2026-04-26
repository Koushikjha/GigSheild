// com/gigshield/user/service/UserService.java
package com.gigshield.user.service;

import com.gigshield.auth.dto.RegisterRequest;
import com.gigshield.config.AppConstants;
import com.gigshield.user.dto.UpdateProfileRequest;
import com.gigshield.user.dto.UserResponse;
import com.gigshield.user.entity.User;
import com.gigshield.user.enums.UserStatus;
import com.gigshield.user.mapper.UserMapper;
import com.gigshield.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository      userRepository;
    private final UserMapper          userMapper;
    private final StringRedisTemplate redisTemplate;

    // PasswordEncoder removed — no passwords

    @Override
    public UserDetails loadUserByUsername(String phone)
            throws UsernameNotFoundException {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + phone));
    }

    /**
     * Called after OTP is verified.
     * If user exists → login flow, return existing user.
     * If user doesn't exist → registration flow, create and return.
     */
    @Transactional
    public User findOrCreate(String phone, RegisterRequest registrationData) {
        return userRepository.findByPhone(phone)
                .orElseGet(() -> {
                    if (registrationData == null) {
                        throw new IllegalArgumentException(
                                "New user must provide registration details " +
                                        "(fullName, city, latitude, longitude)");
                    }
                    validateRegistrationData(registrationData);
                    User newUser = userMapper.toEntity(registrationData);
                    log.info("New user registered: phone={}", phone);
                    return userRepository.save(newUser);
                });
    }

    public User findByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + phone));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + id));
    }

    public UserResponse getProfile(String phone) {
        return userMapper.toResponse(findByPhone(phone));
    }

    @Transactional
    public UserResponse updateProfile(String phone,
                                      UpdateProfileRequest request) {
        User user = findByPhone(phone);
        user.setFullName(request.getFullName());
        if (request.getWeeklyIncomeEstimate() != null) {
            user.setWeeklyIncomeEstimate(
                    request.getWeeklyIncomeEstimate());
        }
        if (request.getLatitude()  != null) {
            user.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            user.setLongitude(request.getLongitude());
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void recordFraudStrike(Long userId) {
        userRepository.incrementFraudStrike(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + userId));

        if (user.getFraudStrikeCount() >= AppConstants.FRAUD_STRIKE_BAN_COUNT) {
            userRepository.updateStatus(userId, UserStatus.BANNED);

            String banKey = "ban:" + user.getPhone();
            redisTemplate.opsForValue().set(
                    banKey, "1",
                    AppConstants.JWT_EXPIRY_MS,
                    TimeUnit.MILLISECONDS);

            log.warn("User {} BANNED — Redis ban key set: {}",
                    userId, banKey);
        }
    }

    public long countAll() {
        return userRepository.count();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void validateRegistrationData(RegisterRequest req) {
        if (req.getFullName() == null || req.getFullName().isBlank()) {
            throw new IllegalArgumentException(
                    "fullName is required for registration");
        }
        if (req.getCity() == null || req.getCity().isBlank()) {
            throw new IllegalArgumentException(
                    "city is required for registration");
        }
        if (req.getLatitude() == null || req.getLongitude() == null) {
            throw new IllegalArgumentException(
                    "latitude and longitude are required for registration");
        }
    }
}