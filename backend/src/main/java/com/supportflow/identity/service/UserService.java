package com.supportflow.identity.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.dto.RegisterRequest;
import com.supportflow.identity.dto.UserResponse;
import com.supportflow.identity.model.RefreshToken;
import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.model.UserRole;
import com.supportflow.identity.repository.RefreshTokenRepository;
import com.supportflow.identity.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserModel registerCustomer(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        UserModel user = new UserModel();
        user.setUsername(request.username().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        user.setGender(request.gender());
        // Self-registration strictly assigns CUSTOMER role to prevent privilege escalation
        user.setRole(UserRole.CUSTOMER.name());

        UserModel saved = userRepository.save(user);
        auditService.recordEvent("USER_REGISTERED", saved.getUsername(), "USER", String.valueOf(saved.getId()),
                "Customer registered with role " + saved.getRole());
        return saved;
    }

    @Transactional
    public UserModel saveUser(UserModel user) {
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole(UserRole.CUSTOMER.name());
        } else {
            user.setRole(UserRole.fromStringOrDefault(user.getRole(), UserRole.CUSTOMER).name());
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listSupportAgents() {
        return userRepository.findAll().stream()
                .filter(u -> UserRole.AGENT.name().equals(u.getRole()) || UserRole.ADMIN.name().equals(u.getRole()))
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional
    public RefreshToken createRefreshToken(UserModel user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString() + "-" + UUID.randomUUID());
        refreshToken.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken verifyAndRotateRefreshToken(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (existing.isRevoked() || existing.isExpired()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired or revoked");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        RefreshToken rotated = createRefreshToken(existing.getUser());
        auditService.recordEvent("TOKEN_REFRESHED", existing.getUser().getUsername(), "USER",
                String.valueOf(existing.getUser().getId()), "Rotated refresh token");
        return rotated;
    }
}
