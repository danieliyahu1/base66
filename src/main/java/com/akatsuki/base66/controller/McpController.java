package com.akatsuki.base66.controller;

import com.akatsuki.base66.dto.McpServerSummary;
import com.akatsuki.base66.dto.McpToggleRequest;
import com.akatsuki.base66.opencode.OpenCodeChatModel;
import com.akatsuki.base66.service.UserWorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/base66/mcp")
public class McpController {

    private final UserWorkspaceService userWorkspaceService;
    private final OpenCodeChatModel openCodeChatModel;

    public McpController(UserWorkspaceService userWorkspaceService, OpenCodeChatModel openCodeChatModel) {
        this.userWorkspaceService = userWorkspaceService;
        this.openCodeChatModel = openCodeChatModel;
    }

    @GetMapping
    public List<McpServerSummary> list() {
        String username = resolveAuthenticatedUsername();
        log.info("GET /api/base66/mcp requested by user='{}'", username);
        List<McpServerSummary> servers = openCodeChatModel.getMcpStatus(username);
        log.debug("MCP list returned {} servers for user='{}'", servers.size(), username);
        return servers;
    }

    @PatchMapping("/{serverName}")
    public McpServerSummary toggle(@PathVariable String serverName,
                                   @Valid @RequestBody McpToggleRequest request) {
        String username = resolveAuthenticatedUsername();
        log.info("PATCH /api/base66/mcp/{} requested by user='{}' enabled={}", serverName, username, request.enabled());

        // 1. Persist the toggle to opencode.json
        boolean configUpdated = userWorkspaceService.setMcpServerEnabled(username, serverName, request.enabled());
        if (!configUpdated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + serverName);
        }

        // 2. Apply the runtime effect (connect or disconnect)
        if (request.enabled()) {
            openCodeChatModel.connectMcpServer(username, serverName);
        } else {
            openCodeChatModel.disconnectMcpServer(username, serverName);
        }

        // 3. Fetch the actual post-toggle status (connect might have failed)
        List<McpServerSummary> allServers = openCodeChatModel.getMcpStatus(username);
        return allServers.stream()
            .filter(s -> serverName.equals(s.name()))
            .findFirst()
            .orElse(new McpServerSummary(serverName, request.enabled() ? "connected" : "disabled", null));
    }

    private String resolveAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated username is required");
        }
        return authentication.getName();
    }
}
