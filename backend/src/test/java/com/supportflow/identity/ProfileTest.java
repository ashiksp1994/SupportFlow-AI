package com.supportflow.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.supportflow.SupportFlowApplication;

@SpringBootTest(classes = SupportFlowApplication.class)
@ActiveProfiles("test")
public class ProfileTest {

    @Autowired
    private Environment environment;

    @Test
    public void testActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            System.out.println("Active Profile: " + profile);
        }
        assertTrue(environment.acceptsProfiles(Profiles.of("test")), "Test profile should be active");
    }
}
