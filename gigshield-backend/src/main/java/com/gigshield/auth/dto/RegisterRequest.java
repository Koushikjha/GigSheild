// com/gigshield/auth/dto/RegisterRequest.java — simplified, no password
package com.gigshield.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$",
            message = "Invalid Indian mobile number")
    private String phone;

    @NotBlank
    private String fullName;

    @NotBlank
    private String city;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private String platform;
}