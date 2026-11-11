package com.supportflow.identity;

import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.supportflow.SupportFlowApplication;
import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.repository.RefreshTokenRepository;
import com.supportflow.identity.repository.UserRepository;
import com.supportflow.identity.security.JwtUtils;
import com.supportflow.identity.service.UserService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SupportFlowApplication.class)
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerShouldDefaultToCustomerRoleAndNeverExposePasswordHash() throws Exception {
        String payload = """
                {
                  "username": "alice_sec",
                  "password": "StrongPassword!123",
                  "fullName": "Alice Security",
                  "email": "alice@supportflow.test"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice_sec"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(content().string(not(containsString("StrongPassword!123"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void loginShouldReturnStructuredTokensAndRejectInvalidCredentialsWith401() throws Exception {
        UserModel agent = new UserModel();
        agent.setUsername("bob_agent");
        agent.setPassword("AgentSecret!123");
        agent.setFullName("Bob Agent");
        agent.setEmail("bob@supportflow.test");
        agent.setRole("AGENT");
        userService.saveUser(agent);

        UserDetails details = userDetailsService.loadUserByUsername("bob_agent");
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority())));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bob_agent",
                                  "password": "AgentSecret!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("AGENT"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bob_agent",
                                  "password": "WrongPassword!"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtWithRefreshTokenHeaderBypassShouldBeRejectedWith401() throws Exception {
        UserModel user = new UserModel();
        user.setUsername("eve_bypass");
        user.setPassword("BypassPass!123");
        user.setRole("ADMIN");
        userService.saveUser(user);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        String expiredToken = Jwts.builder()
                .issuer(JwtUtils.ISSUER)
                .subject("eve_bypass")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me?refreshToken=true")
                        .header("Authorization", "Bearer " + expiredToken)
                        .header("isRefreshToken", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtWithMissingOrInvalidIssuerShouldBeRejectedWith401() throws Exception {
        UserModel user = new UserModel();
        user.setUsername("issuer_test_user");
        user.setPassword("IssuerPass!123");
        user.setRole("ADMIN");
        userService.saveUser(user);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        long now = System.currentTimeMillis();

        // 1. Signed token with NO issuer claim (null issuer) must be rejected
        String missingIssuerToken = Jwts.builder()
                .subject("issuer_test_user")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 600_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + missingIssuerToken))
                .andExpect(status().isUnauthorized());

        // 2. Signed token with wrong issuer claim must be rejected
        String wrongIssuerToken = Jwts.builder()
                .issuer("forged-external-issuer")
                .subject("issuer_test_user")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 600_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + wrongIssuerToken))
                .andExpect(status().isUnauthorized());
    }
}
