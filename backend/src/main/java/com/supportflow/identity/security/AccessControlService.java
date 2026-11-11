package com.supportflow.identity.security;

import java.util.Objects;
import java.util.function.Supplier;

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
 * Enforces fail-closed authentication, horizontal (organisation/customer) isolation,
 * and vertical (role) separation. Unauthenticated or anonymous callers are never
 * granted SYSTEM or ADMIN privileges.
 */
@Service
public class AccessControlService {

    private static final ThreadLocal<Boolean> SYSTEM_EXECUTION_CONTEXT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public record CallerContext(
            Long userId,
            String username,
            String email,
            UserRole role,
            Long organisationId,
            boolean httpAuthenticated
    ) {
        public boolean isAdmin() {
            return httpAuthenticated && role == UserRole.ADMIN;
        }

        public boolean isStaff() {
            return httpAuthenticated && (role == UserRole.ADMIN || role == UserRole.AGENT);
        }

        public boolean isCustomer() {
            return httpAuthenticated && role == UserRole.CUSTOMER;
        }
    }

    private final UserRepository userRepository;

    public AccessControlService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Executes an internal system operation (such as non-production startup seeding or
     * trusted background maintenance) within an explicit system context.
     */
    public <T> T supplyAsSystem(Supplier<T> action) {
        boolean previous = Boolean.TRUE.equals(SYSTEM_EXECUTION_CONTEXT.get());
        SYSTEM_EXECUTION_CONTEXT.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (previous) {
                SYSTEM_EXECUTION_CONTEXT.set(Boolean.TRUE);
            } else {
                SYSTEM_EXECUTION_CONTEXT.remove();
            }
        }
    }

    public void runAsSystem(Runnable action) {
        supplyAsSystem(() -> {
            action.run();
            return null;
        });
    }

    public CallerContext currentCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            if (Boolean.TRUE.equals(SYSTEM_EXECUTION_CONTEXT.get())) {
                return new CallerContext(null, "SYSTEM", null, UserRole.ADMIN, null, true);
            }
            return new CallerContext(null, "ANONYMOUS", null, null, null, false);
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

    public CallerContext requireAuthenticated() {
        CallerContext caller = currentCaller();
        if (!caller.httpAuthenticated() || caller.role() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required: anonymous or unauthenticated access is forbidden.");
        }
        return caller;
    }

    public String requireActorUsername(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return requireAuthenticated().username();
    }

    public void requireStaff() {
        CallerContext caller = requireAuthenticated();
        if (!caller.isStaff()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: staff role (ADMIN or AGENT) required.");
        }
    }

    public void requireAdmin() {
        CallerContext caller = requireAuthenticated();
        if (!caller.isAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: ADMIN role required.");
        }
    }

    /**
     * Verifies that the current caller is authenticated and permitted to access data belonging to
     * {@code resourceOrganisationId} or owned by {@code resourceOwnerIdentifier} (email or username).
     */
    public void verifyTenantAccess(Long resourceOrganisationId, String resourceOwnerIdentifier) {
        CallerContext caller = requireAuthenticated();

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

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
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
