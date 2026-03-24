package com.akatsuki.base66.security;

import com.akatsuki.base66.config.AuthProperties;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class InMemoryAuthUserDetailsService implements UserDetailsService {

    private final Map<String, UserDetails> usersByUsername;

    public InMemoryAuthUserDetailsService(AuthProperties authProperties, PasswordEncoder passwordEncoder) {
        this.usersByUsername = new HashMap<>();

        for (AuthProperties.AuthUser configuredUser : authProperties.getUsers()) {
            UserDetails userDetails = User
                .withUsername(configuredUser.getUsername())
                .password(passwordEncoder.encode(configuredUser.getPassword()))
                .roles(configuredUser.getRoles().toArray(new String[0]))
                .build();

            UserDetails previous = usersByUsername.putIfAbsent(userDetails.getUsername(), userDetails);
            if (previous != null) {
                throw new IllegalStateException("Duplicate username configured: " + userDetails.getUsername());
            }
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDetails userDetails = usersByUsername.get(username);
        if (userDetails == null) {
            throw new UsernameNotFoundException("Unknown user");
        }
        return userDetails;
    }
}
