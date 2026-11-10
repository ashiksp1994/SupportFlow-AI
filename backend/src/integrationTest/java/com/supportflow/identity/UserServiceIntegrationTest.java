package com.supportflow.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.supportflow.SupportFlowApplication;
import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.repository.UserRepository;
import com.supportflow.identity.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SupportFlowApplication.class)
@ActiveProfiles("integration")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void shouldPersistAndRetrieveUserWithEncodedPassword() {
        UserModel user = new UserModel();
        user.setFullName("Ada Lovelace");
        user.setEmail("ada@supportflow.test");
        user.setUsername("adalovelace");
        user.setPassword("PlaintextPass!123");
        user.setRole("AGENT");

        UserModel savedUser = userService.saveUser(user);

        assertNotNull(savedUser.getId());
        assertEquals("adalovelace", savedUser.getUsername());
        assertNotEquals("PlaintextPass!123", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("PlaintextPass!123", savedUser.getPassword()));

        UserModel foundUser = userService.findByUsername("adalovelace");
        assertNotNull(foundUser);
        assertEquals(savedUser.getId(), foundUser.getId());
        assertEquals("AGENT", foundUser.getRole());
    }
}
