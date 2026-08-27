// com/gigshield/auth/dto/VerifyOtpRequest.java
package com.gigshield.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$",
            message = "Invalid Indian mobile number")
    private String phone;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otp;

    // ── Registration fields — only required for new users ────────────────────
    // All optional — if user already exists these are ignored

    private String fullName;

    private String city;

    private Double latitude;

    private Double longitude;

    private String platform;   // ZOMATO | SWIGGY | OTHER
}