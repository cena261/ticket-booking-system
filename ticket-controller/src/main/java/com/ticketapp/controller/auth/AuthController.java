package com.ticketapp.controller.auth;

import com.ticketapp.application.auth.AuthAppService;
import com.ticketapp.application.auth.AuthenticatedUser;
import com.ticketapp.application.auth.TokenPair;
import com.ticketapp.controller.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthAppService authAppService;

    public AuthController(AuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenPair> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authAppService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenPair> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authAppService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authAppService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authAppService.logout(request.refreshToken());
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<AuthenticatedUser> me(Authentication authentication) {
        return ApiResponse.ok(authAppService.currentUser((Long) authentication.getPrincipal()));
    }
}
