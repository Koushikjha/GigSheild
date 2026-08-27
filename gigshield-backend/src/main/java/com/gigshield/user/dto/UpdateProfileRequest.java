// com/gigshield/user/dto/UpdateProfileRequest.java
package com.gigshield.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank
    private String fullName;

    @Positive
    private Double weeklyIncomeEstimate;

    private Double latitude;
    private Double longitude;
}