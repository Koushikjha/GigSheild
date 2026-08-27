// com/gigshield/auth/dto/AuthResponse.java
package com.gigshield.auth.dto;

import lombok.*;

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;    // "Bearer"
    private long   expiresIn;    // seconds
}