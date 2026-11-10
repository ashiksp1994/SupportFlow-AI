package com.supportflow.identity.model;

public enum UserRole {
    CUSTOMER,
    AGENT,
    ADMIN;

    public static UserRole fromStringOrDefault(String value, UserRole defaultRole) {
        if (value == null || value.isBlank()) {
            return defaultRole;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        if ("USER".equals(normalized)) {
            return CUSTOMER;
        }
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return defaultRole;
        }
    }
}
