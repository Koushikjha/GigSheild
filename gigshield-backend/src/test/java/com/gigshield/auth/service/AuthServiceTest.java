// src/test/java/com/gigshield/auth/service/AuthServiceTest.java
package com.gigshield.auth.service;

import com.gigshield.auth.dto.AuthResponse;
import com.gigshield.auth.dto.SendOtpRequest;
import com.gigshield.auth.dto.VerifyOtpRequest;
import com.gigshield.auth.repository.RefreshTokenRepository;
import com.gigshield.auth.util.JwtUtil;
import com.gigshield.user.entity.User;
import com.gigshield.user.enums.UserRole;
import com.gigshield.user.enums.UserStatus;
import com.gigshield.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock OtpService             otpService;
    @Mock JwtUtil                jwtUtil;
    @Mock UserService            userService;
    @Mock RefreshTokenRepository refreshTokenRepo;

    @InjectMocks AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .phone("9876543210")
                .fullName("Test Worker")
                .role(UserRole.ROLE_WORKER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void sendOtp_shouldDelegateToOtpService() {
        SendOtpRequest req = new SendOtpRequest();
        req.setPhone("9876543210");

        authService.sendOtp(req);

        verify(otpService).generateAndSend("9876543210");
    }

    @Test
    void verifyOtp_existingUser_shouldReturnTokens() {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setPhone("9876543210");
        req.setOtp("123456");
        // No registration fields — login flow

        doNothing().when(otpService).verify("9876543210", "123456");
        when(userService.findOrCreate(eq("9876543210"), isNull()))
                .thenReturn(mockUser);
        when(jwtUtil.generateAccessToken(anyString(), anyMap()))
                .thenReturn("access-token");
        when(refreshTokenRepo.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.verifyOtp(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void verifyOtp_newUser_shouldCreateUserAndReturnTokens() {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setPhone("9876543210");
        req.setOtp("123456");
        req.setFullName("New Worker");
        req.setCity("Delhi");
        req.setLatitude(28.6);
        req.setLongitude(77.2);

        doNothing().when(otpService).verify("9876543210", "123456");
        when(userService.findOrCreate(eq("9876543210"), any()))
                .thenReturn(mockUser);
        when(jwtUtil.generateAccessToken(anyString(), anyMap()))
                .thenReturn("access-token");
        when(refreshTokenRepo.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.verifyOtp(req);

        assertThat(response.getAccessToken()).isNotBlank();
        verify(userService).findOrCreate(eq("9876543210"), any());
    }

    @Test
    void verifyOtp_bannedUser_shouldThrow() {
        mockUser.setStatus(UserStatus.BANNED);

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setPhone("9876543210");
        req.setOtp("123456");

        doNothing().when(otpService).verify("9876543210", "123456");
        when(userService.findOrCreate(any(), any()))
                .thenReturn(mockUser);

        assertThatThrownBy(() -> authService.verifyOtp(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void verifyOtp_invalidOtp_shouldThrow() {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setPhone("9876543210");
        req.setOtp("000000");

        doThrow(new IllegalArgumentException("Incorrect OTP. 2 attempt(s) remaining."))
                .when(otpService).verify("9876543210", "000000");

        assertThatThrownBy(() -> authService.verifyOtp(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incorrect OTP");

        verifyNoInteractions(userService);
    }
}