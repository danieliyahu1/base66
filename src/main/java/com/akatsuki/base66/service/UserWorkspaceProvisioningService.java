package com.akatsuki.base66.service;

import com.akatsuki.base66.config.AuthProperties;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserWorkspaceProvisioningService {

    private final AuthProperties authProperties;
    private final UserWorkspaceService userWorkspaceService;

    public UserWorkspaceProvisioningService(
        AuthProperties authProperties,
        UserWorkspaceService userWorkspaceService
    ) {
        this.authProperties = authProperties;
        this.userWorkspaceService = userWorkspaceService;
    }

    public Path ensureWorkspaceReady(String username) {
        try {
            Path workspace = userWorkspaceService.getUserWorkspace(username);
            log.debug("Workspace ready for user='{}' path={}", username, workspace);
            return workspace;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to prepare workspace for user=" + username, ex);
        }
    }

    public void ensureConfiguredUsersWorkspacesReady() {
        for (AuthProperties.AuthUser authUser : authProperties.getUsers()) {
            ensureWorkspaceReady(authUser.getUsername());
        }
    }
}
