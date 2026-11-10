package com.supportflow.identity;

import org.junit.jupiter.api.Test;

import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.model.UserRole;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserModelTest {

    @Test
    void shouldDefaultToCustomerRoleAndStoreOrganisationId() {
        UserModel user = new UserModel();
        user.setId(1L);
        user.setUsername("customer_user");
        user.setPassword("hashed_secret");
        user.setOrganisationId(42L);

        assertEquals(1L, user.getId());
        assertEquals("customer_user", user.getUsername());
        assertEquals(UserRole.CUSTOMER.name(), user.getRole());
        assertEquals(42L, user.getOrganisationId());
    }

    @Test
    void shouldSupportAdminRoleAndValueEquality() {
        UserModel user1 = new UserModel();
        user1.setId(2L);
        user1.setUsername("admin");
        user1.setPassword("adminpass");
        user1.setRole(UserRole.ADMIN.name());

        UserModel user2 = new UserModel();
        user2.setId(2L);
        user2.setUsername("admin");
        user2.setPassword("adminpass");
        user2.setRole(UserRole.ADMIN.name());

        assertAll("user",
                () -> assertEquals(2L, user1.getId()),
                () -> assertEquals("admin", user1.getUsername()),
                () -> assertEquals(UserRole.ADMIN.name(), user1.getRole()),
                () -> assertEquals(user1, user2),
                () -> assertEquals(user1.hashCode(), user2.hashCode())
        );
    }
}
