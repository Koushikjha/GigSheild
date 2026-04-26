// com/gigshield/user/mapper/UserMapper.java
package com.gigshield.user.mapper;

import com.gigshield.auth.dto.RegisterRequest;
import com.gigshield.config.AppConstants;
import com.gigshield.user.dto.UserResponse;
import com.gigshield.user.entity.User;
import com.gigshield.user.enums.DeliveryPlatform;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .city(user.getCity())
                .latitude(user.getLatitude())
                .longitude(user.getLongitude())
                .platform(user.getPlatform())
                .status(user.getStatus())
                .fraudStrikeCount(user.getFraudStrikeCount())
                .weeklyIncomeEstimate(user.getWeeklyIncomeEstimate())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // No encodedPassword param — OTP auth, no password stored
    public User toEntity(RegisterRequest req) {
        return User.builder()
                .phone(req.getPhone())
                .fullName(req.getFullName())
                .city(req.getCity())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .platform(req.getPlatform() != null
                        ? DeliveryPlatform.valueOf(
                        req.getPlatform().toUpperCase())
                        : DeliveryPlatform.OTHER)
                .weeklyIncomeEstimate(
                        AppConstants.DEFAULT_WEEKLY_INCOME_INR)
                .build();
    }
}