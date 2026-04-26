// com/gigshield/user/dto/UserResponse.java
package com.gigshield.user.dto;

import com.gigshield.user.enums.DeliveryPlatform;
import com.gigshield.user.enums.UserStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder
public class UserResponse {
    private Long             id;
    private String           phone;
    private String           fullName;
    private String           city;
    private Double           latitude;
    private Double           longitude;
    private DeliveryPlatform platform;
    private UserStatus       status;
    private int              fraudStrikeCount;
    private Double           weeklyIncomeEstimate;
    private LocalDateTime    createdAt;
}