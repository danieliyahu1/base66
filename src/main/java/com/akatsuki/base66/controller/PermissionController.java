package com.akatsuki.base66.controller;

import com.akatsuki.base66.dto.PendingPermissionResponse;
import com.akatsuki.base66.dto.PermissionReplyRequest;
import com.akatsuki.base66.dto.PermissionReplyResponse;
import com.akatsuki.base66.opencode.OpenCodeChatModel;
import com.akatsuki.base66.opencode.OpenCodePermissionRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/base66/permissions")
public class PermissionController {

    private static final Set<String> ALLOWED_REPLIES = Set.of("once", "always", "reject");
    private static final Set<String> LOGGED_PENDING_REQUESTS = ConcurrentHashMap.newKeySet();

    private final OpenCodeChatModel openCodeChatModel;

    public PermissionController(@Qualifier("openCodeChatModel") ChatModel chatModel) {
        if (!(chatModel instanceof OpenCodeChatModel model)) {
            throw new IllegalStateException("openCodeChatModel bean must be OpenCodeChatModel");
        }
        this.openCodeChatModel = model;
    }

    @GetMapping("/pending")
    public List<PendingPermissionResponse> pending() {
        String username = resolveAuthenticatedUsername();
        List<OpenCodePermissionRequest> permissions = openCodeChatModel.getPendingPermissions(username);

        for (OpenCodePermissionRequest permission : permissions) {
            String requestId = permission.getId();
            if (requestId != null && LOGGED_PENDING_REQUESTS.add(requestId)) {
                log.info(
                    "OpenCode permission requested. user={} requestId={} permission={} patterns={}",
                    username,
                    requestId,
                    permission.getPermission(),
                    permission.getPatterns()
                );
            }
        }

        return permissions.stream()
            .map(permission -> new PendingPermissionResponse(
                permission.getId(),
                permission.getPermission(),
                permission.getPatterns() == null ? List.of() : permission.getPatterns()))
            .toList();
    }

    @PostMapping("/{requestId}/reply")
    public PermissionReplyResponse reply(
        @PathVariable String requestId,
        @Valid @RequestBody PermissionReplyRequest request
    ) {
        String username = resolveAuthenticatedUsername();
        String reply = request.reply().trim().toLowerCase();
        if (!ALLOWED_REPLIES.contains(reply)) {
            throw new IllegalArgumentException("reply must be one of: once, always, reject");
        }

        if (!openCodeChatModel.isPermissionRequestOwnedByUser(username, requestId)) {
            log.warn("Rejecting permission reply for non-owned request. user={} requestId={}", username, requestId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission request does not belong to this user");
        }

        log.info("Replying to pending permission request. user={} requestId={} reply={}", username, requestId, reply);
        boolean success = openCodeChatModel.replyPermission(username, requestId, reply);
        LOGGED_PENDING_REQUESTS.remove(requestId);
        log.info("Permission reply result. user={} requestId={} success={}", username, requestId, success);
        return new PermissionReplyResponse(success);
    }

    private String resolveAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Authenticated username is required");
        }
        return authentication.getName();
    }
}
