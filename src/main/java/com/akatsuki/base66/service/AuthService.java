package com.akatsuki.base66.service;

import com.akatsuki.base66.dto.LoginRequest;
import com.akatsuki.base66.dto.LoginResponse;
import com.akatsuki.base66.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserWorkspaceService userWorkspaceService;

    public AuthService(
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        UserWorkspaceService userWorkspaceService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userWorkspaceService = userWorkspaceService;
    }

    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt: username='{}' password='{}'", request.username(), request.password());
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtService.generateToken(userDetails);
            String agentName = userWorkspaceService.getRequiredAgentName(userDetails.getUsername());

            log.info("Login success: username='{}' agent='{}'", userDetails.getUsername(), agentName);
            return new LoginResponse(userDetails.getUsername(), token, "Bearer", agentName);
        } catch (Exception ex) {
            log.warn("Login failed for username='{}': {}", request.username(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
