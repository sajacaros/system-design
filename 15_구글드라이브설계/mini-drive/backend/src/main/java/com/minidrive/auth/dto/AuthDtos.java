package com.minidrive.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record SignupRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank @Size(max = 100) String nickname) {
    }

    public record SignupResponse(Long id, String email, String nickname) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record UserSummary(Long id, String email, String nickname) {
    }

    public record LoginResponse(String accessToken, String refreshToken, UserSummary user) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record RefreshResponse(String accessToken, String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }
}
