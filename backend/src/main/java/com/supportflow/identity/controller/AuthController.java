package com.supportflow.identity.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.security.JwtUtils;
import com.supportflow.identity.service.UserService;

/**
 * Authentication endpoints for user registration and login.
 *
 * <p>SECURITY BLOCKER NOTE (Tracked in GitHub Issue #5 for Phase 2 remediation):
 * Registration currently binds {@link UserModel} directly as request/response body.
 * Phase 2 replaces this with validated request/response DTO records and removes password hash exposure.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/register")
    public UserModel register(@RequestBody UserModel user) {
        return userService.saveUser(user);
    }

    @PostMapping("/login")
    public String login(@RequestBody UserModel user) throws Exception {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
            );
        } catch (Exception e) {
            throw new Exception("Invalid username or password");
        }
        final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        return jwtUtils.generateToken(userDetails);
    }
}
