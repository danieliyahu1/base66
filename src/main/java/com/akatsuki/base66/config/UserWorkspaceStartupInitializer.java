package com.akatsuki.base66.config;

import com.akatsuki.base66.service.UserWorkspaceProvisioningService;
import com.akatsuki.base66.service.OpenCodeProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@Slf4j
public class UserWorkspaceStartupInitializer implements ApplicationRunner {

    private final UserWorkspaceProvisioningService provisioningService;
    private final OpenCodeProcessService openCodeProcessService;

    public UserWorkspaceStartupInitializer(
        UserWorkspaceProvisioningService provisioningService,
        OpenCodeProcessService openCodeProcessService
    ) {
        this.provisioningService = provisioningService;
        this.openCodeProcessService = openCodeProcessService;
    }

    @Override
    public void run(ApplicationArguments args) {
        openCodeProcessService.ensureStartedIfNeeded();
        log.info("Preparing user workspaces for all configured users before application startup completes");
        provisioningService.ensureConfiguredUsersWorkspacesReady();
    }
}
