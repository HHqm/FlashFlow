package com.flashflow.trade.auth;

import com.flashflow.trade.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ApiResponse<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
