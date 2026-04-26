// com/gigshield/auth/dto/SendOtpRequest.java
package com.gigshield.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendOtpRequest {

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$",
            message = "Invalid Indian mobile number")
    private String phone;
}