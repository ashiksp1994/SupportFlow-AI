package com.supportflow.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportflow.identity.model.UserModel;

public interface UserRepository extends JpaRepository<UserModel, Long> {
    UserModel findByUsername(String username);
}
