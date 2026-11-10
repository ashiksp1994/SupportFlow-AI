package com.supportflow.identity.security;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.model.UserRole;
import com.supportflow.identity.repository.UserRepository;

/**
 * Central multi-tenant and role-based access control enforcement service.
 * Enforces horizontal (organisation/customer) isolation and vertical (role) separation.
 */
@Service
public class AccessControlService {

    public record CallerContext(
            Long userId,
            String username,
            String email,
            UserRole role,
            Long organisationId,
            boolean httpAuthenticated
    ) {
        public boolean isAdmin() {
            return role == UserRole.ADMIN;
        }

        public boolean isStaff() {
            return role == UserRole.ADMIN || role == UserRole.AGENT;
        }

        public boolean isCustomer() {
            return role == UserRole.CUSTOMER;
        }
    }

    private final UserRepository userRepository;

    public AccessControlService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public CallerContext currentCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new CallerContext(null, "SYSTEM", null, UserRole.ADMIN, null, false);
        }

        String username = auth.getName();
        UserModel dbUser = userRepository.findByUsername(username);
        if (dbUser != null) {
            UserRole role = UserRole.fromStringOrDefault(dbUser.getRole(), UserRole.CUSTOMER);
            return new CallerContext(
                    dbUser.getId(),
                    dbUser.getUsername(),
                    dbUser.getEmail(),
                    role,
                    dbUser.getOrganisationId(),
                    true
            );
        }

        UserRole authorityRole = resolveRoleFromAuthorities(auth);
        return new CallerContext(null, username, null, authorityRole, null, true);
    }

    private UserRole resolveRoleFromAuthorities(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String granted = authority.getAuthority();
            if ("ROLE_ADMIN".equals(granted)) {
                return UserRole.ADMIN;
            }
            if ("ROLE_AGENT".equals(granted)) {
                return UserRole.AGENT;
            }
        }
        return UserRole.CUSTOMER;
    }

    public void requireStaff() {
        CallerContext caller = currentCaller();
        if (caller.httpAuthenticated() && !caller.isStaff()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: staff role (ADMIN or AGENT) required.");
        }
    }

    public void requireAdmin() {
        CallerContext caller = currentCaller();
        if (caller.httpAuthenticated() && !caller.isAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: ADMIN role required.");
        }
    }

    /**
     * Verifies that the current caller is permitted to access data belonging to {@code resourceOrganisationId}
     * or owned by {@code resourceOwnerIdentifier} (email or username).
     */
    public void verifyTenantAccess(Long resourceOrganisationId, String resourceOwnerIdentifier) {
        CallerContext caller = currentCaller();
        if (!caller.httpAuthenticated()) {
            return;
        }

        // Platform ADMINs have global visibility; platform AGENTs without a single-tenant lock manage cross-org queues.
        if (caller.isAdmin()) {
            return;
        }
        if (caller.role() == UserRole.AGENT && caller.organisationId() == null) {
            return;
        }

        // Tenant-bound AGENT or CUSTOMER: enforce strict organisation match
        if (caller.organisationId() != null) {
            if (resourceOrganisationId != null && Objects.equals(caller.organisationId(), resourceOrganisationId)) {
                return;
            }
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: resource belongs to another organisation.");
        }

        // Unassigned CUSTOMER without organisationId: can only access resources matching their own email/username
        if (caller.isCustomer()) {
            if (resourceOwnerIdentifier != null
                    && (resourceOwnerIdentifier.equalsIgnoreCase(caller.username())
                    || (caller.email() != null && resourceOwnerIdentifier.equalsIgnoreCase(caller.email())))) {
                return;
            }
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: customer is not authorized to access this resource.");
        }
    }

    public boolean canAccessTenant(Long resourceOrganisationId, String resourceOwnerIdentifier) {
        try {
            verifyTenantAccess(resourceOrganisationId, resourceOwnerIdentifier);
            return true;
        } catch (ResponseStatusException ex) {
            return false;
        }
    }
}
