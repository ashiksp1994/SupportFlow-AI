package com.supportflow.identity.service;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.repository.UserRepository;

/**
 * Loads user details from the database for Spring Security authentication.
 *
 * <p>SECURITY BLOCKER NOTE (Tracked in GitHub Issue #6 for Phase 2 remediation):
 * Authorities are currently initialized as an empty list rather than mapping {@code user.getRole()}.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserModel user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                new ArrayList<>());
    }
}
