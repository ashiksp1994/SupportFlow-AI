package com.supportflow.identity.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.identity.dto.AuthResponse;
import com.supportflow.identity.dto.LoginRequest;
import com.supportflow.identity.dto.RefreshTokenRequest;
import com.supportflow.identity.dto.RegisterRequest;
import com.supportflow.identity.dto.UserResponse;
import com.supportflow.identity.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = userService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(userService.refreshToken(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) Map<String, String> payload,
            Authentication authentication) {
        String refreshToken = payload != null ? payload.get("refreshToken") : null;
        String username = authentication != null ? authentication.getName() : null;
        if ((refreshToken == null || refreshToken.isBlank()) && (username == null || username.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token or active session required for logout");
        }
        userService.logout(refreshToken, username);
        return ResponseEntity.ok(Map.of("status", "LOGGED_OUT"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return ResponseEntity.ok(userService.getProfileByUsername(authentication.getName()));
    }
}
