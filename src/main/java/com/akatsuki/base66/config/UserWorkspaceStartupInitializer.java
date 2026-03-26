package com.akatsuki.base66.config;

import com.akatsuki.base66.opencode.OpenCodeChatModel;
import com.akatsuki.base66.service.UserWorkspaceProvisioningService;
import com.akatsuki.base66.service.OpenCodeProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(0)
@Slf4j
public class UserWorkspaceStartupInitializer implements ApplicationRunner {

    private final UserWorkspaceProvisioningService provisioningService;
    private final OpenCodeProcessService openCodeProcessService;
    private final AuthProperties authProperties;
    private final OpenCodeChatModel openCodeChatModel;

    public UserWorkspaceStartupInitializer(
        UserWorkspaceProvisioningService provisioningService,
        OpenCodeProcessService openCodeProcessService,
        AuthProperties authProperties,
        @Qualifier("openCodeChatModel") ChatModel chatModel
    ) {
        this.provisioningService = provisioningService;
        this.openCodeProcessService = openCodeProcessService;
        this.authProperties = authProperties;
        if (!(chatModel instanceof OpenCodeChatModel model)) {
            throw new IllegalStateException("openCodeChatModel bean must be OpenCodeChatModel");
        }
        this.openCodeChatModel = model;
    }

    @Override
    public void run(ApplicationArguments args) {
        openCodeProcessService.ensureStartedIfNeeded();
        log.info("Preparing user workspaces for all configured users before application startup completes");
        provisioningService.ensureConfiguredUsersWorkspacesReady();

        List<String> usernames = authProperties.getUsers().stream()
            .map(AuthProperties.AuthUser::getUsername)
            .toList();
        log.info("Validating required user-specific OpenCode agents before startup completes");
        openCodeChatModel.assertRequiredAgentsConfigured(usernames);
    }
}
