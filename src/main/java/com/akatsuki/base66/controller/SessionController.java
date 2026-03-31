package com.akatsuki.base66.controller;

import com.akatsuki.base66.dto.CreateSessionRequest;
import com.akatsuki.base66.dto.RenameSessionRequest;
import com.akatsuki.base66.dto.SessionResponse;
import com.akatsuki.base66.dto.SessionSelectResponse;
import com.akatsuki.base66.opencode.OpenCodeChatModel;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/base66/sessions")
public class SessionController {

    private final OpenCodeChatModel openCodeChatModel;

    public SessionController(@Qualifier("openCodeChatModel") ChatModel chatModel) {
        if (!(chatModel instanceof OpenCodeChatModel model)) {
            throw new IllegalStateException("openCodeChatModel bean must be OpenCodeChatModel");
        }
        this.openCodeChatModel = model;
    }

    @GetMapping
    public List<SessionResponse> list(@RequestParam(defaultValue = "20") int limit) {
        String username = resolveAuthenticatedUsername();
        log.info("Session list requested by user='{}' with limit={}", username, limit);
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        List<SessionResponse> sessions = openCodeChatModel.listSessions(username, effectiveLimit).stream()
            .map(session -> new SessionResponse(
                session.id(),
                session.title(),
                session.createdAt(),
                session.updatedAt(),
                session.active()))
            .toList();
        log.info("Session list returned {} sessions for user='{}'", sessions.size(), username);
        return sessions;
    }

    @PostMapping
    public SessionResponse create(@RequestBody(required = false) CreateSessionRequest request) {
        String username = resolveAuthenticatedUsername();
        String title = request == null ? null : request.title();
        log.info("Session create requested by user='{}' with title='{}'", username, title);
        OpenCodeChatModel.OpenCodeSessionInfo session = openCodeChatModel.createSession(username, title);
        log.info("Session created: id='{}' title='{}' for user='{}'", session.id(), session.title(), username);
        return new SessionResponse(
            session.id(),
            session.title(),
            session.createdAt(),
            session.updatedAt(),
            session.active());
    }

    @PostMapping("/{sessionId}/select")
    public SessionSelectResponse select(@PathVariable String sessionId) {
        String username = resolveAuthenticatedUsername();
        log.info("Session select requested by user='{}' for sessionId='{}'", username, sessionId);
        boolean success = openCodeChatModel.selectSession(username, sessionId);
        log.info("Session select result for user='{}' sessionId='{}': {}", username, sessionId, success);
        return new SessionSelectResponse(success);
    }

    @PatchMapping("/{sessionId}")
    public SessionResponse rename(@PathVariable String sessionId, @RequestBody RenameSessionRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            log.warn("Session rename failed: missing title for sessionId='{}'", sessionId);
            throw new IllegalArgumentException("title is required");
        }

        String username = resolveAuthenticatedUsername();
        log.info("Session rename requested by user='{}' for sessionId='{}' to title='{}'", username, sessionId, request.title());
        OpenCodeChatModel.OpenCodeSessionInfo session = openCodeChatModel.renameSession(username, sessionId, request.title());
        log.info("Session renamed: id='{}' newTitle='{}' for user='{}'", session.id(), session.title(), username);
        return new SessionResponse(
            session.id(),
            session.title(),
            session.createdAt(),
            session.updatedAt(),
            session.active());
    }

    @DeleteMapping("/{sessionId}")
    public SessionSelectResponse delete(@PathVariable String sessionId) {
        String username = resolveAuthenticatedUsername();
        log.info("Session delete requested by user='{}' for sessionId='{}'", username, sessionId);
        boolean success = openCodeChatModel.deleteSession(username, sessionId);
        log.info("Session delete result for user='{}' sessionId='{}': {}", username, sessionId, success);
        return new SessionSelectResponse(success);
    }

    private String resolveAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Authenticated username is required");
        }
        return authentication.getName();
    }
}
