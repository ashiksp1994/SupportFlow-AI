package com.supportflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SupportFlowApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring Boot application context and Flyway migrations start cleanly
    }

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(SupportFlowApplication.class).verify();
    }
}
