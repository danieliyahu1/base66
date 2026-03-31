package com.akatsuki.base66.service;

import com.akatsuki.base66.dto.CreateSkillFromTextResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class UserWorkspaceService {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-zA-Z0-9._-]+");
    private static final Set<String> ROOT_CONFIG_FILES = Set.of(
        "opencode.json",
        "opencode.jsonc",
        "tui.json",
        "tui.jsonc",
        "AGENTS.md"
    );
    private static final List<String> OPENCODE_CONFIG_DIRS = List.of(
        "agents",
        "commands",
        "modes",
        "plugins",
        "skills",
        "tools",
        "themes"
    );
    private static final Pattern SKILL_PATH_PATTERN = Pattern.compile("(?m)^SKILL_PATH:\\s*(\\S+)\\s*$");
    private static final Pattern SAFE_SKILL_FILE_PATTERN =
        Pattern.compile("^\\.opencode/skills/[a-zA-Z0-9._-]+/SKILL\\.md$");

    private final Path workspacesRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserWorkspaceService(@Value("${app.user-workspaces-root:.user-workspaces}") String workspacesRoot) {
        this.workspacesRoot = Paths.get(workspacesRoot).toAbsolutePath().normalize();
    }

    public Path getUserWorkspace(String username) {
        String safeUsername = sanitizeUsername(username);
        Path workspace = workspacesRoot.resolve(safeUsername).normalize();
        ensureDirectory(workspace);
        initializeWorkspaceConfigStructure(workspace, safeUsername);
        return workspace;
    }

    public String getUserWorkspaceAsString(String username) {
        return getUserWorkspace(username).toString();
    }

    public String getUserWorkspaceId(String username) {
        return "workspace-" + sanitizeUsername(username);
    }

    public String getRequiredAgentName(String username) {
        return sanitizeUsername(username) + "-agent";
    }

    public String readUserConfigFile(String username, String relativePath) {
        Path path = resolveUserConfigPath(username, relativePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Config file does not exist");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read config file", e);
        }
    }

    public Map<String, Object> getUserWorkspaceConfig(String username) {
        Path workspace = getUserWorkspace(username);
        Path rootConfigPath = workspace.resolve("opencode.json").normalize();

        try {
            ObjectNode root = readOrCreateConfig(rootConfigPath);
            return objectMapper.convertValue(root, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read user workspace config", e);
        }
    }

    public String buildSkillCreationPrompt(String skillName, String freeText) {
        String safeSkillName = validateSkillName(skillName);
        String safeText = validateSkillText(freeText);
        String expectedPath = toSkillRelativePath(safeSkillName);
        return String.join("\n", List.of(
            "You are operating inside the current workspace.",
            "Create exactly one skill file from the user content.",
            "Requirements:",
            "1) Use the provided skill name exactly as-is. Do not rename it.",
            "2) Write the file ONLY at " + expectedPath,
            "3) The file must include valid SKILL.md frontmatter and body.",
            "4) Set frontmatter `name` to the provided skill name.",
            "5) Generate ONLY the `description` frontmatter value from the user content.",
            "6) Keep the skill body aligned with the user-provided content.",
            "7) Do not create any other file.",
            "8) After writing the file, reply with one line only in this exact format:",
            "SKILL_PATH: " + expectedPath,
            "Provided skill name:",
            safeSkillName,
            "User free-text content:",
            safeText
        ));
    }

    public CreateSkillFromTextResponse verifySkillCreationResult(String username, String skillName, String modelResponse) {
        String safeUsername = sanitizeUsername(username);
        String safeSkillName = validateSkillName(skillName);
        String expectedPath = toSkillRelativePath(safeSkillName);
        String skillPath = extractSkillPath(modelResponse);

        if (!StringUtils.hasText(skillPath)) {
            log.warn("Skill creation failed. user={} reason=missing_skill_path", safeUsername);
            return new CreateSkillFromTextResponse(
                false,
                null,
                "Skill creation failed. OpenCode did not return a skill path."
            );
        }

        if (!SAFE_SKILL_FILE_PATTERN.matcher(skillPath).matches()) {
            log.warn("Skill creation failed. user={} path={} reason=unsafe_path", safeUsername, skillPath);
            return new CreateSkillFromTextResponse(
                false,
                null,
                "Skill creation failed. OpenCode returned an invalid skill path."
            );
        }

        if (!expectedPath.equals(skillPath)) {
            log.warn(
                "Skill creation failed. user={} expectedPath={} actualPath={} reason=path_mismatch",
                safeUsername,
                expectedPath,
                skillPath
            );
            return new CreateSkillFromTextResponse(
                false,
                null,
                "Skill creation failed. OpenCode returned a path that does not match the requested skill name."
            );
        }

        try {
            String content = readUserConfigFile(safeUsername, expectedPath);
            if (!isValidSkillFileContent(content, safeSkillName)) {
                log.warn("Skill creation failed. user={} path={} reason=invalid_content", safeUsername, skillPath);
                return new CreateSkillFromTextResponse(
                    false,
                    null,
                    "Skill creation failed. Generated SKILL.md content is invalid or missing required frontmatter."
                );
            }

            log.info("Skill created successfully. user={} path={}", safeUsername, skillPath);
            return new CreateSkillFromTextResponse(true, skillPath, "Skill created successfully.");
        } catch (IllegalArgumentException ex) {
            log.warn("Skill creation failed. user={} path={} reason=file_missing", safeUsername, skillPath);
            return new CreateSkillFromTextResponse(
                false,
                null,
                "Skill creation failed. OpenCode did not create SKILL.md in workspace."
            );
        }
    }

    private Path resolveUserConfigPath(String username, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }

        String normalizedRelative = relativePath.trim().replace('\\', '/');
        while (normalizedRelative.startsWith("./")) {
            normalizedRelative = normalizedRelative.substring(2);
        }

        Path workspace = getUserWorkspace(username).normalize();
        Path resolved = workspace.resolve(normalizedRelative).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IllegalArgumentException("Invalid path");
        }

        Path parent = resolved.getParent();
        String fileName = resolved.getFileName() == null ? "" : resolved.getFileName().toString();
        if (parent != null && parent.equals(workspace) && ROOT_CONFIG_FILES.contains(fileName)) {
            return resolved;
        }

        Path opencodeBase = workspace.resolve(".opencode").normalize();
        if (!resolved.startsWith(opencodeBase)) {
            throw new IllegalArgumentException("Invalid path");
        }

        return resolved;
    }

    private String validateSkillText(String freeText) {
        if (!StringUtils.hasText(freeText)) {
            throw new IllegalArgumentException("content is required");
        }
        return freeText.trim();
    }

    private String validateSkillName(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            throw new IllegalArgumentException("skillName is required");
        }

        String trimmed = skillName.trim();
        if (!SAFE_SEGMENT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("skillName contains unsupported characters");
        }
        return trimmed;
    }

    private String toSkillRelativePath(String skillName) {
        return ".opencode/skills/" + skillName + "/SKILL.md";
    }

    private boolean isValidSkillFileContent(String content, String expectedSkillName) {
        if (!StringUtils.hasText(content) || !content.contains("---")) {
            return false;
        }

        Pattern namePattern = Pattern.compile(
            "(?m)^name:\\s*\"?" + Pattern.quote(expectedSkillName) + "\"?\\s*$"
        );
        Pattern descriptionPattern = Pattern.compile("(?m)^description:\\s*.+$");
        return namePattern.matcher(content).find() && descriptionPattern.matcher(content).find();
    }

    private String extractSkillPath(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }

        Matcher matcher = SKILL_PATH_PATTERN.matcher(response);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1).trim().replace('\\', '/');
    }

    private String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (!SAFE_SEGMENT.matcher(username).matches()) {
            throw new IllegalArgumentException("username contains unsupported characters");
        }
        return username;
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to create workspace directory", e);
        }
    }

    private void initializeWorkspaceConfigStructure(Path workspace, String username) {
        Path opencodeDir = workspace.resolve(".opencode");
        ensureDirectory(opencodeDir);
        for (String dirName : OPENCODE_CONFIG_DIRS) {
            ensureDirectory(opencodeDir.resolve(dirName));
        }

        boolean rootConfigChanged = initializeOrUpdateRootOpencodeConfig(workspace.resolve("opencode.json"), workspace, username);
        boolean tuiChanged = initializeTuiConfigIfMissing(workspace.resolve("tui.json"));
        boolean agentsMdChanged = initializeAgentsGuide(workspace.resolve("AGENTS.md"), username);
        if (rootConfigChanged || tuiChanged || agentsMdChanged) {
            log.info("Initialized per-user OpenCode config structure in workspace {}", workspace);
        }
    }

    private boolean initializeOrUpdateRootOpencodeConfig(Path opencodeConfig, Path workspace, String username) {
        try {
            boolean configExisted = Files.exists(opencodeConfig);
            ObjectNode root = readOrCreateConfig(opencodeConfig);
            boolean changed = false;
            String requiredAgentName = getRequiredAgentName(username);

            if (!root.has("$schema")) {
                root.put("$schema", "https://opencode.ai/config.json");
                changed = true;
            }

            ObjectNode permission;
            JsonNode permissionNode = root.get("permission");
            if (permissionNode instanceof ObjectNode existingPermission) {
                permission = existingPermission;
            } else {
                permission = objectMapper.createObjectNode();
                root.set("permission", permission);
                changed = true;
            }
            if (!"deny".equals(permission.path("external_directory").asText(null))) {
                permission.put("external_directory", "deny");
                changed = true;
            }

            // OpenCode runtime Config schema does not include `workspace`.
            // If older Base66 versions wrote it, remove it to keep session creation valid.
            if (root.has("workspace")) {
                root.remove("workspace");
                changed = true;
            }

            if (!requiredAgentName.equals(root.path("default_agent").asText(null))) {
                root.put("default_agent", requiredAgentName);
                changed = true;
            }

            ObjectNode agentsNode;
            JsonNode existingAgentsNode = root.get("agent");
            if (existingAgentsNode instanceof ObjectNode existingObjectNode) {
                agentsNode = existingObjectNode;
            } else {
                agentsNode = objectMapper.createObjectNode();
                root.set("agent", agentsNode);
                changed = true;
            }

            ObjectNode userAgentNode;
            JsonNode existingUserAgentNode = agentsNode.get(requiredAgentName);
            if (existingUserAgentNode instanceof ObjectNode existingObjectNode) {
                userAgentNode = existingObjectNode;
            } else {
                userAgentNode = objectMapper.createObjectNode();
                agentsNode.set(requiredAgentName, userAgentNode);
                changed = true;
            }

            if (!"primary".equals(userAgentNode.path("mode").asText(null))) {
                userAgentNode.put("mode", "primary");
                changed = true;
            }
            // Do not set agent-level permission — let the workspace-level permission config govern.
            // Remove any previously set agent-level permission so it does not override workspace defaults.
            if (userAgentNode.has("permission")) {
                userAgentNode.remove("permission");
                changed = true;
            }

            String expectedDescription = "Dedicated Base66 agent for user " + username;
            if (!expectedDescription.equals(userAgentNode.path("description").asText(null))) {
                userAgentNode.put("description", expectedDescription);
                changed = true;
            }

            String expectedPrompt = String.join("\n", List.of(
                "You are the dedicated Base66 agent for user " + username + ".",
                "Use all built-in tools when needed to complete tasks safely and accurately.",
                "Use both global and project skills when relevant.",
                "Use both global and project MCP integrations when relevant.",
                "Respect global and project configuration while operating in this workspace."
            ));
            if (!expectedPrompt.equals(userAgentNode.path("prompt").asText(null))) {
                userAgentNode.put("prompt", expectedPrompt);
                changed = true;
            }

            // Bootstrap Playwright MCP for newly created user workspaces using isolated browser data.
            if (!configExisted) {
                ObjectNode mcpNode;
                JsonNode existingMcpNode = root.get("mcp");
                if (existingMcpNode instanceof ObjectNode existingObjectNode) {
                    mcpNode = existingObjectNode;
                } else {
                    mcpNode = objectMapper.createObjectNode();
                    root.set("mcp", mcpNode);
                    changed = true;
                }

                String userDataDir = workspace.resolve(".browser-data").toString();
                JsonNode existingPlaywrightNode = mcpNode.get("playwright");
                if (!(existingPlaywrightNode instanceof ObjectNode existingPlaywrightObject)) {
                    ObjectNode playwrightNode = objectMapper.createObjectNode();
                    playwrightNode.put("type", "local");
                    ArrayNode commandArray = playwrightNode.putArray("command");
                    commandArray.add("npx");
                    commandArray.add("-y");
                    commandArray.add("@playwright/mcp@latest");
                    commandArray.add("--user-data-dir");
                    commandArray.add(userDataDir);
                    playwrightNode.put("enabled", true);
                    mcpNode.set("playwright", playwrightNode);
                    changed = true;
                } else {
                    boolean playwrightChanged = false;

                    if (!"local".equals(existingPlaywrightObject.path("type").asText(null))) {
                        existingPlaywrightObject.put("type", "local");
                        playwrightChanged = true;
                    }

                    JsonNode commandNode = existingPlaywrightObject.get("command");
                    if (!(commandNode instanceof ArrayNode commandArrayNode)
                        || commandArrayNode.size() != 5
                        || !"npx".equals(commandArrayNode.path(0).asText(null))
                        || !"-y".equals(commandArrayNode.path(1).asText(null))
                        || !"@playwright/mcp@latest".equals(commandArrayNode.path(2).asText(null))
                        || !"--user-data-dir".equals(commandArrayNode.path(3).asText(null))
                        || !userDataDir.equals(commandArrayNode.path(4).asText(null))) {
                        ArrayNode replacement = objectMapper.createArrayNode();
                        replacement.add("npx");
                        replacement.add("-y");
                        replacement.add("@playwright/mcp@latest");
                        replacement.add("--user-data-dir");
                        replacement.add(userDataDir);
                        existingPlaywrightObject.set("command", replacement);
                        playwrightChanged = true;
                    }

                    if (!existingPlaywrightObject.path("enabled").asBoolean(false)) {
                        existingPlaywrightObject.put("enabled", true);
                        playwrightChanged = true;
                    }

                    if (playwrightChanged) {
                        changed = true;
                    }
                }
            }

            if (!changed) {
                return false;
            }

            String updated = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(opencodeConfig, updated + System.lineSeparator(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize scoped OpenCode config", e);
        }
    }

    private boolean initializeTuiConfigIfMissing(Path tuiConfigPath) {
        try {
            if (Files.exists(tuiConfigPath) && Files.size(tuiConfigPath) > 0) {
                return false;
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.put("$schema", "https://opencode.ai/tui.json");

            String updated = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(tuiConfigPath, updated + System.lineSeparator(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize scoped OpenCode tui config", e);
        }
    }

    private boolean initializeAgentsGuide(Path agentsPath, String username) {
        try {
            String expected = buildAgentsGuideContent(username);
            if (Files.exists(agentsPath)) {
                String existing = Files.readString(agentsPath, StandardCharsets.UTF_8);
                if (expected.equals(existing)) {
                    return false;
                }
            }

            Files.writeString(agentsPath, expected, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize user AGENTS.md", e);
        }
    }

    private String buildAgentsGuideContent(String username) {
        String requiredAgentName = getRequiredAgentName(username);
        return String.join(System.lineSeparator(), List.of(
            "# Base66 User Agent Guide",
            "",
            "User: " + username,
            "Required agent: " + requiredAgentName,
            "",
            "This workspace is configured to always use the required agent above.",
            "The required agent is configured in opencode.json as the default agent.",
            "",
            "Required behavior:",
            "- Use all built-in tools when needed.",
            "- Use both global and project skills when relevant.",
            "- Use both global and project MCP integrations when relevant.",
            "- Respect global and project configuration.",
            ""
        ));
    }

    private ObjectNode readOrCreateConfig(Path opencodeConfig) throws IOException {
        if (!Files.exists(opencodeConfig)) {
            return objectMapper.createObjectNode();
        }

        String raw = Files.readString(opencodeConfig, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            return objectMapper.createObjectNode();
        }

        JsonNode parsed = objectMapper.readTree(raw);
        if (parsed instanceof ObjectNode objectNode) {
            return objectNode;
        }

        log.warn("opencode.json at {} is not an object. Replacing with scoped object config.", opencodeConfig);
        return objectMapper.createObjectNode();
    }
}
