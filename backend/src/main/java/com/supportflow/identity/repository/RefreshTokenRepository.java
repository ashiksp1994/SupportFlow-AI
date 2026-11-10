package com.supportflow.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportflow.identity.model.RefreshToken;
import com.supportflow.identity.model.UserModel;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(UserModel user);
}
