package com.akatsuki.base66.opencode;

import com.akatsuki.base66.service.UserWorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class OpenCodeChatModel implements ChatModel {

    private static final long MIN_TTL_SECONDS = 60;

    private final WebClient webClient;
    private final UserWorkspaceService userWorkspaceService;
    private final Map<String, SessionEntry> sessionsByUser = new ConcurrentHashMap<>();
    private final long sessionTtlMillis;

    public OpenCodeChatModel(String baseUrl, UserWorkspaceService userWorkspaceService, long sessionTtlSeconds) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.userWorkspaceService = userWorkspaceService;
        long effectiveTtlSeconds = Math.max(MIN_TTL_SECONDS, sessionTtlSeconds);
        this.sessionTtlMillis = effectiveTtlSeconds * 1000;
        log.info("OpenCodeChatModel created for baseUrl={} sessionTtlSeconds={}", baseUrl, effectiveTtlSeconds);
    }

    private SessionEntry initSession(String username) {
        Scope scope = scopeForUser(username);
        applyUserWorkspaceConfig(scope, username);
        assertRequiredAgentConfigured(username);
        log.info("Initializing OpenCode session for user={}", username);

        Map<String, Object> sessionRequest = new LinkedHashMap<>();
        sessionRequest.put("title", "Base66-" + username);

        Map<?, ?> response = webClient.post()
                .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/session"))
                .bodyValue(sessionRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("id")) {
            String sessionId = response.get("id").toString();
            verifyScopedPath(scope);
            log.info("OpenCode session initialized for user={} id={}", username, sessionId);
            return new SessionEntry(sessionId, System.currentTimeMillis());
        }

        throw new IllegalStateException("OpenCode session response did not include an id");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // Fallback standard call using WebClient synchronously
        return stream(prompt).blockLast();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        String userText = prompt.getContents();
        String username = resolveAuthenticatedUsername();
        Scope scope = scopeForUser(username);
        String requiredAgentName = userWorkspaceService.getRequiredAgentName(username);
        assertRequiredAgentConfigured(username);
        String sessionId = getOrCreateSessionId(username);

        log.info("Streaming chat request to OpenCode. length={}", userText == null ? 0 : userText.length());
        var requestPayload = new OpenCodePromptRequest(List.of(new TextPartInput(userText)), requiredAgentName);

        return webClient.post()
            .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/session/{id}/message", sessionId))
                .bodyValue(requestPayload)
                .retrieve()
                // Retrieve the response as a stream of OpenCodePromptResponse objects
                .bodyToFlux(OpenCodePromptResponse.class)
                .map(response -> {
                    // Extract only final text chunks, ignoring other part types.
                    String finalText = response.getParts().stream()
                        .filter(part -> "text".equals(part.getType()))
                        .map(OpenCodePart::getText)
                            .collect(Collectors.joining(""));
                    log.debug("Received OpenCode stream chunk. length={}", finalText.length());

                    // Yield the chunk back to Spring AI
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(finalText))));
                });
    }

    public String getOrCreateSessionId(String username) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        if (safeUsername.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }

        long now = System.currentTimeMillis();
        SessionEntry sessionEntry = sessionsByUser.compute(safeUsername, (ignored, existing) -> {
            if (existing == null || isExpired(existing, now)) {
                return initSession(safeUsername);
            }
            return existing;
        });

        return Objects.requireNonNull(sessionEntry, "sessionEntry is required").sessionId();
    }

    public List<OpenCodePermissionRequest> getPendingPermissions(String username) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        if (safeUsername.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }

        SessionEntry sessionEntry = sessionsByUser.get(safeUsername);
        if (sessionEntry == null || sessionEntry.sessionId().isBlank()) {
            return List.of();
        }

        String sessionId = sessionEntry.sessionId();
        Scope scope = scopeForUser(safeUsername);

        List<OpenCodePermissionRequest> permissions;
        try {
            permissions = webClient.get()
                .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/permission"))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<OpenCodePermissionRequest>>() {})
                .block();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.warn("OpenCode server does not support /permission endpoint yet");
                return List.of();
            }
            throw ex;
        }

        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }

        return permissions.stream()
            .filter(permission -> sessionId.equals(permission.getSessionID()))
            .collect(Collectors.toList());
    }

    public List<OpenCodeSessionInfo> listSessions(String username, int limit) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        if (safeUsername.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }

        Scope scope = scopeForUser(safeUsername);
        List<Map<String, Object>> sessions = webClient.get()
            .uri(uriBuilder -> {
                UriBuilder scoped = uriBuilder
                    .path("/session")
                    .queryParam("directory", scope.directory())
                    .queryParam("limit", Math.max(1, limit));

                Optional.ofNullable(scope.workspaceId())
                    .filter(id -> id.startsWith("wrk"))
                    .ifPresent(id -> scoped.queryParam("workspace", id));

                return scoped.build();
            })
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .block();

        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        String activeSessionId = Optional.ofNullable(sessionsByUser.get(safeUsername))
            .map(SessionEntry::sessionId)
            .orElse(null);

        List<OpenCodeSessionInfo> result = new ArrayList<>();
        for (Map<String, Object> rawSession : sessions) {
            OpenCodeSessionInfo parsed = parseSession(rawSession, activeSessionId);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    public OpenCodeSessionInfo createSession(String username, String title) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        if (safeUsername.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }

        Scope scope = scopeForUser(safeUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (title != null && !title.isBlank()) {
            payload.put("title", title.trim());
        }

        Map<String, Object> response = webClient.post()
            .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/session"))
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        if (response == null) {
            throw new IllegalStateException("OpenCode did not return a created session");
        }

        String sessionId = Objects.toString(response.get("id"), "").trim();
        if (sessionId.isEmpty()) {
            throw new IllegalStateException("OpenCode session response did not include an id");
        }

        sessionsByUser.put(safeUsername, new SessionEntry(sessionId, System.currentTimeMillis()));
        OpenCodeSessionInfo parsed = parseSession(response, sessionId);
        if (parsed != null) {
            return parsed;
        }

        return new OpenCodeSessionInfo(sessionId, title == null ? "" : title.trim(), 0L, 0L, true);
    }

    public boolean selectSession(String username, String sessionId) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        String safeSessionId = Objects.requireNonNull(sessionId, "sessionId is required").trim();
        if (safeUsername.isEmpty() || safeSessionId.isEmpty()) {
            throw new IllegalArgumentException("username and sessionId are required");
        }

        Scope scope = scopeForUser(safeUsername);
        Map<String, Object> session = webClient.get()
            .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/session/{id}", safeSessionId))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        if (session == null || !safeSessionId.equals(Objects.toString(session.get("id"), "").trim())) {
            return false;
        }

        sessionsByUser.put(safeUsername, new SessionEntry(safeSessionId, System.currentTimeMillis()));
        return true;
    }

    public OpenCodeSessionInfo renameSession(String username, String sessionId, String title) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        String safeSessionId = Objects.requireNonNull(sessionId, "sessionId is required").trim();
        String safeTitle = Objects.requireNonNull(title, "title is required").trim();
        if (safeUsername.isEmpty() || safeSessionId.isEmpty() || safeTitle.isEmpty()) {
            throw new IllegalArgumentException("username, sessionId and title are required");
        }

        Scope scope = scopeForUser(safeUsername);
        Map<String, Object> updated = webClient.patch()
            .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/session/{id}", safeSessionId))
            .bodyValue(Map.of("title", safeTitle))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        if (updated == null) {
            throw new IllegalStateException("OpenCode did not return updated session");
        }

        OpenCodeSessionInfo parsed = parseSession(updated, getActiveSessionId(safeUsername));
        if (parsed != null) {
            return parsed;
        }
        return new OpenCodeSessionInfo(safeSessionId, safeTitle, 0L, 0L, safeSessionId.equals(getActiveSessionId(safeUsername)));
    }

    public boolean deleteSession(String username, String sessionId) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        String safeSessionId = Objects.requireNonNull(sessionId, "sessionId is required").trim();
        if (safeUsername.isEmpty() || safeSessionId.isEmpty()) {
            throw new IllegalArgumentException("username and sessionId are required");
        }

        Scope scope = scopeForUser(safeUsername);
        Boolean deleted = webClient.delete()
            .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/session/{id}", safeSessionId))
            .retrieve()
            .bodyToMono(Boolean.class)
            .block();

        if (!Boolean.TRUE.equals(deleted)) {
            return false;
        }

        String activeSessionId = getActiveSessionId(safeUsername);
        if (safeSessionId.equals(activeSessionId)) {
            sessionsByUser.remove(safeUsername);
        }
        return true;
    }

    private String getActiveSessionId(String username) {
        return Optional.ofNullable(sessionsByUser.get(username))
            .map(SessionEntry::sessionId)
            .orElse(null);
    }

    private OpenCodeSessionInfo parseSession(Map<String, Object> raw, String activeSessionId) {
        if (raw == null) {
            return null;
        }

        String id = Objects.toString(raw.get("id"), "").trim();
        if (id.isEmpty()) {
            return null;
        }

        String title = Objects.toString(raw.get("title"), "");
        Object timeNode = raw.get("time");
        long createdAt = 0L;
        long updatedAt = 0L;
        if (timeNode instanceof Map<?, ?> timeMap) {
            createdAt = toLong(timeMap.get("created"));
            updatedAt = toLong(timeMap.get("updated"));
        }

        return new OpenCodeSessionInfo(id, title, createdAt, updatedAt, id.equals(activeSessionId));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public boolean isPermissionRequestOwnedByUser(String username, String requestId) {
        String safeRequestId = Objects.requireNonNull(requestId, "requestId is required").trim();
        if (safeRequestId.isEmpty()) {
            throw new IllegalArgumentException("requestId is required");
        }

        return getPendingPermissions(username).stream()
            .map(OpenCodePermissionRequest::getId)
            .anyMatch(safeRequestId::equals);
    }

    public boolean replyPermission(String username, String requestId, String reply) {
        String safeRequestId = Objects.requireNonNull(requestId, "requestId is required").trim();
        String safeReply = Objects.requireNonNull(reply, "reply is required").trim();
        if (safeRequestId.isEmpty() || safeReply.isEmpty()) {
            throw new IllegalArgumentException("requestId and reply are required");
        }

        if (!isPermissionRequestOwnedByUser(username, safeRequestId)) {
            log.warn("Rejecting permission reply that is not owned by user. user={} requestId={}", username, safeRequestId);
            return false;
        }

        Scope scope = scopeForUser(username);
        try {
            Boolean result = webClient.post()
                .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/permission/{requestId}/reply", safeRequestId))
                .bodyValue(Map.of("reply", safeReply))
                .retrieve()
                .bodyToMono(Boolean.class)
                .block();

            return Boolean.TRUE.equals(result);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) {
                throw ex;
            }

            String sessionId = getOrCreateSessionId(username);
            Boolean legacyResult = webClient.post()
                .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/session/{sessionId}/permissions/{permissionId}", sessionId, safeRequestId))
                .bodyValue(Map.of("response", safeReply))
                .retrieve()
                .bodyToMono(Boolean.class)
                .block();

            return Boolean.TRUE.equals(legacyResult);
        }
    }

    public void assertRequiredAgentConfigured(String username) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        if (safeUsername.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }

        Scope scope = scopeForUser(safeUsername);
        String requiredAgentName = userWorkspaceService.getRequiredAgentName(safeUsername);
        List<String> availableAgents = listAvailableAgents(scope);

        if (!availableAgents.contains(requiredAgentName)) {
            throw new IllegalStateException(
                "Required agent '" + requiredAgentName + "' is not available for user '" + safeUsername + "'"
            );
        }
    }

    public void assertRequiredAgentsConfigured(List<String> usernames) {
        List<String> missingAgents = new ArrayList<>();
        for (String username : usernames) {
            try {
                assertRequiredAgentConfigured(username);
            } catch (RuntimeException ex) {
                String label = username == null ? "<null>" : username;
                missingAgents.add(label + " (" + ex.getMessage() + ")");
            }
        }

        if (!missingAgents.isEmpty()) {
            throw new IllegalStateException(
                "Required user agents are missing or invalid: " + String.join("; ", missingAgents)
            );
        }
    }

    private List<String> listAvailableAgents(Scope scope) {
        List<Map<String, Object>> agents = webClient.get()
            .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/agent"))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .block();

        if (agents == null || agents.isEmpty()) {
            return List.of();
        }

        return agents.stream()
            .map(agent -> agent.get("name"))
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.toList());
    }

    private String resolveAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Authenticated username is required");
        }
        return authentication.getName();
    }

    private Scope scopeForUser(String username) {
        String safeUsername = Objects.requireNonNull(username, "username is required").trim();
        if (safeUsername.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }

        String directory = userWorkspaceService.getUserWorkspaceAsString(safeUsername);
        String workspaceId = userWorkspaceService.getUserWorkspaceId(safeUsername);
        return new Scope(directory, workspaceId);
    }

    private void verifyScopedPath(Scope scope) {
        Map<?, ?> pathInfo = webClient.get()
            .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/path"))
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (pathInfo == null) {
            throw new IllegalStateException("OpenCode did not return path information for scoped workspace");
        }

        Object directory = pathInfo.get("directory");
        if (directory == null || !pathMatches(scope.directory(), directory.toString())) {
            throw new IllegalStateException("OpenCode scoped directory mismatch");
        }
    }

    private void applyUserWorkspaceConfig(Scope scope, String username) {
        Map<String, Object> config = userWorkspaceService.getUserWorkspaceConfig(username);
        if (config == null || config.isEmpty()) {
            return;
        }

        Map<String, Object> runtimeConfig = new LinkedHashMap<>(config);
        // File-local path controls are not part of OpenCode runtime /config PATCH schema.
        runtimeConfig.remove("workspace");
        if (runtimeConfig.isEmpty()) {
            return;
        }

        try {
            webClient.patch()
                .uri(uriBuilder -> scopedUri(uriBuilder, scope, "/config"))
                .bodyValue(runtimeConfig)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        } catch (WebClientResponseException ex) {
            log.warn(
                "OpenCode /config patch failed for user={} status={} body={}. Continuing without runtime patch.",
                username,
                ex.getStatusCode().value(),
                ex.getResponseBodyAsString()
            );
        }
    }

    private URI scopedUri(UriBuilder uriBuilder, Scope scope, String path, Object... pathVariables) {
        UriBuilder scoped = uriBuilder
            .path(path)
            .queryParam("directory", scope.directory());

        // OpenCode uses workspace IDs with a "wrk" prefix. Passing ad-hoc IDs can fail session creation.
        Optional.ofNullable(scope.workspaceId())
            .filter(id -> id.startsWith("wrk"))
            .ifPresent(id -> scoped.queryParam("workspace", id));

        return scoped.build(pathVariables);
    }

    private boolean isExpired(SessionEntry entry, long now) {
        return now - entry.createdAtMillis() > sessionTtlMillis;
    }

    private boolean pathMatches(String expected, String actual) {
        String expectedNormalized = expected.replace('/', '\\');
        String actualNormalized = actual.replace('/', '\\');
        return expectedNormalized.equalsIgnoreCase(actualNormalized);
    }

    private record Scope(String directory, String workspaceId) {
    }

    private record SessionEntry(String sessionId, long createdAtMillis) {
    }

    public record OpenCodeSessionInfo(
        String id,
        String title,
        long createdAt,
        long updatedAt,
        boolean active
    ) {
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().build();
    }
}