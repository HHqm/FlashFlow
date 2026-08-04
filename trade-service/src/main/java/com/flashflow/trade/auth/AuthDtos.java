package com.flashflow.trade.auth;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String role
    ) {
    }
}
