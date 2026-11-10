package com.supportflow.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.dto.AuthResponse;
import com.supportflow.identity.dto.LoginRequest;
import com.supportflow.identity.dto.RegisterRequest;
import com.supportflow.identity.dto.UserResponse;
import com.supportflow.identity.model.RefreshToken;
import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.model.UserRole;
import com.supportflow.identity.repository.RefreshTokenRepository;
import com.supportflow.identity.repository.UserRepository;
import com.supportflow.identity.security.JwtUtils;

@Service
public class UserService {

    private record LoginAttemptBucket(int count, Instant windowStart) {}

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtUtils jwtUtils;
    private final AuditService auditService;
    private final long refreshTokenExpirationMs;
    private final int maxLoginAttemptsPerMinute;
    private final Map<String, LoginAttemptBucket> loginAttemptBuckets = new ConcurrentHashMap<>();

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            UserDetailsServiceImpl userDetailsService,
            JwtUtils jwtUtils,
            AuditService auditService,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs,
            @Value("${security.auth.max-login-attempts-per-minute:10}") int maxLoginAttemptsPerMinute) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.auditService = auditService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.maxLoginAttemptsPerMinute = maxLoginAttemptsPerMinute;
    }

    @Transactional
    public UserResponse registerCustomer(RegisterRequest request) {
        String normalizedUsername = request.username().trim();
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (request.email() != null && !request.email().isBlank() && userRepository.existsByEmail(request.email().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        UserModel user = new UserModel();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email() != null ? request.email().trim() : null);
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        user.setGender(request.gender());
        user.setOrganisationId(request.organisationId());
        // Public registration always creates a CUSTOMER account — never trust client-supplied role.
        user.setRole(UserRole.CUSTOMER.name());

        UserModel saved = userRepository.save(user);
        auditService.recordEvent("USER_REGISTERED", saved.getUsername(), "USER", String.valueOf(saved.getId()),
                "Registered new CUSTOMER user account");
        return UserResponse.fromEntity(saved);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        checkLoginRateLimit(username);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password())
            );
        } catch (BadCredentialsException ex) {
            recordFailedLoginAttempt(username);
            auditService.recordEvent("AUTH_LOGIN_FAILED", username, "USER", username, "Invalid credentials");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        loginAttemptBuckets.remove(username.toLowerCase());
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UserModel user = userRepository.findByUsername(username);
        String accessToken = jwtUtils.generateToken(userDetails);
        String rawRefreshToken = createRefreshToken(user);

        auditService.recordEvent("AUTH_LOGIN_SUCCESS", username, "USER", String.valueOf(user.getId()),
                "Issued access and refresh tokens");
        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtUtils.getAccessTokenExpirationMs(),
                UserResponse.fromEntity(user)
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse refreshToken(String refreshTokenValue) {
        String hashed = hashToken(refreshTokenValue.trim());
        RefreshToken existing = refreshTokenRepository.findByToken(hashed)
                .or(() -> refreshTokenRepository.findByToken(refreshTokenValue.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (existing.isRevoked()) {
            // Refresh token reuse detected: revoke all active tokens for this user
            UserModel compromisedUser = existing.getUser();
            List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(compromisedUser);
            for (RefreshToken token : activeTokens) {
                token.setRevoked(true);
            }
            refreshTokenRepository.saveAll(activeTokens);
            auditService.recordEvent(
                    "SECURITY_TOKEN_REUSE_DETECTED",
                    compromisedUser.getUsername(),
                    "USER",
                    String.valueOf(compromisedUser.getId()),
                    "Revoked refresh token was replayed; invalidated all active refresh sessions for user.");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token reuse detected; all sessions have been revoked for security.");
        }

        if (existing.isExpired()) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        UserModel user = existing.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String newAccessToken = jwtUtils.generateToken(userDetails);
        String newRawRefreshToken = createRefreshToken(user);

        auditService.recordEvent("AUTH_TOKEN_REFRESHED", user.getUsername(), "USER", String.valueOf(user.getId()),
                "Rotated refresh token");
        return AuthResponse.of(
                newAccessToken,
                newRawRefreshToken,
                jwtUtils.getAccessTokenExpirationMs(),
                UserResponse.fromEntity(user)
        );
    }

    @Transactional
    public void logout(String rawRefreshToken, String authenticatedUsername) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hashed = hashToken(rawRefreshToken.trim());
            refreshTokenRepository.findByToken(hashed)
                    .or(() -> refreshTokenRepository.findByToken(rawRefreshToken.trim()))
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    });
        }
        if (authenticatedUsername != null && !authenticatedUsername.isBlank()) {
            UserModel user = userRepository.findByUsername(authenticatedUsername);
            if (user != null) {
                List<RefreshToken> active = refreshTokenRepository.findByUserAndRevokedFalse(user);
                for (RefreshToken token : active) {
                    token.setRevoked(true);
                }
                refreshTokenRepository.saveAll(active);
                auditService.recordEvent("AUTH_LOGOUT", user.getUsername(), "USER", String.valueOf(user.getId()),
                        "User logged out and revoked active refresh tokens");
            }
        }
    }

    private String createRefreshToken(UserModel user) {
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(hashToken(rawToken));
        token.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        token.setRevoked(false);
        refreshTokenRepository.save(token);
        return rawToken;
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private void checkLoginRateLimit(String username) {
        String key = username.toLowerCase();
        LoginAttemptBucket bucket = loginAttemptBuckets.get(key);
        Instant now = Instant.now();
        if (bucket != null) {
            if (now.isAfter(bucket.windowStart().plusSeconds(60))) {
                loginAttemptBuckets.remove(key);
            } else if (bucket.count() >= maxLoginAttemptsPerMinute) {
                auditService.recordEvent("AUTH_RATE_LIMITED", username, "USER", username,
                        "Blocked login attempt due to rate limit (" + maxLoginAttemptsPerMinute + "/min)");
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed login attempts. Please wait 60 seconds before retrying.");
            }
        }
    }

    private void recordFailedLoginAttempt(String username) {
        String key = username.toLowerCase();
        Instant now = Instant.now();
        loginAttemptBuckets.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plusSeconds(60))) {
                return new LoginAttemptBucket(1, now);
            }
            return new LoginAttemptBucket(existing.count() + 1, existing.windowStart());
        });
    }

    public void resetLoginRateLimits() {
        loginAttemptBuckets.clear();
    }

    @Transactional
    public UserModel saveUser(UserModel user) {
        if (user.getPassword() != null && !user.getPassword().startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        UserRole role = UserRole.fromStringOrDefault(user.getRole(), UserRole.CUSTOMER);
        user.setRole(role.name());
        return userRepository.save(user);
    }

    public UserModel findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public UserResponse getProfileByUsername(String username) {
        UserModel user = userRepository.findOptionalByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.fromEntity(user);
    }
}
