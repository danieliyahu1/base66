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
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        return openCodeChatModel.listSessions(username, effectiveLimit).stream()
            .map(session -> new SessionResponse(
                session.id(),
                session.title(),
                session.createdAt(),
                session.updatedAt(),
                session.active()))
            .toList();
    }

    @PostMapping
    public SessionResponse create(@RequestBody(required = false) CreateSessionRequest request) {
        String username = resolveAuthenticatedUsername();
        String title = request == null ? null : request.title();
        OpenCodeChatModel.OpenCodeSessionInfo session = openCodeChatModel.createSession(username, title);
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
        boolean success = openCodeChatModel.selectSession(username, sessionId);
        return new SessionSelectResponse(success);
    }

    @PatchMapping("/{sessionId}")
    public SessionResponse rename(@PathVariable String sessionId, @RequestBody RenameSessionRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        String username = resolveAuthenticatedUsername();
        OpenCodeChatModel.OpenCodeSessionInfo session = openCodeChatModel.renameSession(username, sessionId, request.title());
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
        boolean success = openCodeChatModel.deleteSession(username, sessionId);
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
