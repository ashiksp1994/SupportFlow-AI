package com.supportflow.identity.dto;

import com.supportflow.identity.model.UserModel;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        String role,
        Long organisationId
) {
    public static UserResponse fromEntity(UserModel user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getOrganisationId()
        );
    }
}
