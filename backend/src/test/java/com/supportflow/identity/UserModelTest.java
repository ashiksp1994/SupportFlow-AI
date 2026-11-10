package com.supportflow.identity;

import org.junit.jupiter.api.Test;

import com.supportflow.identity.model.UserModel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserModelTest {

    @Test
    public void testUserModel() {
        UserModel user = new UserModel();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("password123");
        user.setRole("USER");

        assertEquals(1L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("password123", user.getPassword());
        assertEquals("USER", user.getRole());
    }

    @Test
    public void testUserModelConstructor() {
        UserModel user = new UserModel();
        user.setId(2L);
        user.setUsername("admin");
        user.setPassword("adminpass");
        user.setRole("ADMIN");

        assertAll("user",
                () -> assertEquals(2L, user.getId()),
                () -> assertEquals("admin", user.getUsername()),
                () -> assertEquals("adminpass", user.getPassword()),
                () -> assertEquals("ADMIN", user.getRole())
        );
    }

    @Test
    public void testUserModelEqualsAndHashCode() {
        UserModel user1 = new UserModel();
        user1.setId(1L);
        user1.setUsername("testuser");
        user1.setPassword("password123");
        user1.setRole("USER");

        UserModel user2 = new UserModel();
        user2.setId(1L);
        user2.setUsername("testuser");
        user2.setPassword("password123");
        user2.setRole("USER");

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }
}
