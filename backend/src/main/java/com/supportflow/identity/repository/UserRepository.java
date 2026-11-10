package com.supportflow.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportflow.identity.model.UserModel;

public interface UserRepository extends JpaRepository<UserModel, Long> {
    UserModel findByUsername(String username);
    Optional<UserModel> findOptionalByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
