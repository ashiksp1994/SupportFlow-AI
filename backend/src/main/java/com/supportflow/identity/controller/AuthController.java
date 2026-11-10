package com.supportflow.identity.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.dto.AuthResponse;
import com.supportflow.identity.dto.LoginRequest;
import com.supportflow.identity.dto.RefreshTokenRequest;
import com.supportflow.identity.dto.RegisterRequest;
import com.supportflow.identity.dto.UserResponse;
import com.supportflow.identity.model.RefreshToken;
import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.security.JwtUtils;
import com.supportflow.identity.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final AuditService auditService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserService userService,
            UserDetailsService userDetailsService,
            JwtUtils jwtUtils,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        UserModel saved = userService.registerCustomer(request);
        return UserResponse.fromEntity(saved);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException e) {
            auditService.recordEvent("USER_LOGIN_FAILED", request.username(), "USER", null, "Invalid credentials");
            throw new BadCredentialsException("Invalid username or password");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        UserModel user = userService.findByUsername(request.username());
        String accessToken = jwtUtils.generateToken(userDetails);
        RefreshToken refreshToken = userService.createRefreshToken(user);

        auditService.recordEvent("USER_LOGIN_SUCCESS", user.getUsername(), "USER", String.valueOf(user.getId()),
                "Authenticated with role " + user.getRole());

        return AuthResponse.of(accessToken, refreshToken.getToken(), jwtUtils.getExpirationMs(), UserResponse.fromEntity(user));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken rotated = userService.verifyAndRotateRefreshToken(request.refreshToken());
        UserModel user = rotated.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtUtils.generateToken(userDetails);
        return AuthResponse.of(accessToken, rotated.getToken(), jwtUtils.getExpirationMs(), UserResponse.fromEntity(user));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        UserModel user = userService.findByUsername(authentication.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return UserResponse.fromEntity(user);
    }

    @GetMapping("/agents")
    public List<UserResponse> listAgents() {
        return userService.listSupportAgents();
    }

    @RestControllerAdvice
    public static class GlobalApiExceptionHandler {

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
            pd.setTitle("Authentication Failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
            pd.setTitle("Validation Error");
            Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            fe -> fe.getField(),
                            fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                            (a, b) -> a
                    ));
            pd.setProperty("errors", fieldErrors);
            return ResponseEntity.badRequest().body(pd);
        }

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
            return ResponseEntity.status(ex.getStatusCode()).body(pd);
        }
    }
}
