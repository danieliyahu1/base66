package com.akatsuki.base66.service;

import com.akatsuki.base66.config.OpenCodeProcessProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class OpenCodeProcessService {

    private final OpenCodeProcessProperties properties;
    private final String baseUrl;

    private volatile Process managedProcess;

    public OpenCodeProcessService(
        OpenCodeProcessProperties properties,
        @Value("${opencode.base-url}") String baseUrl
    ) {
        this.properties = properties;
        this.baseUrl = baseUrl;
    }

    public synchronized void ensureStartedIfNeeded() {
        if (!properties.isAutostart()) {
            log.info("OpenCode autostart disabled; expecting external OpenCode at {}", baseUrl);
            return;
        }

        Endpoint endpoint = parseEndpoint(baseUrl);
        if (isReachable(endpoint, Duration.ofSeconds(1))) {
            log.info("OpenCode already reachable at {}:{}", endpoint.host(), endpoint.port());
            return;
        }

        if (managedProcess != null && managedProcess.isAlive()) {
            waitForReachability(endpoint);
            return;
        }

        List<String> command = buildCommand();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        if (StringUtils.hasText(properties.getWorkingDirectory())) {
            Path workingDir = Paths.get(properties.getWorkingDirectory().trim()).toAbsolutePath().normalize();
            processBuilder.directory(workingDir.toFile());
            log.info("Starting OpenCode in working directory {}", workingDir);
        }

        try {
            log.info("Starting OpenCode process: {}", String.join(" ", command));
            managedProcess = processBuilder.start();
            startLogPump(managedProcess);
            waitForReachability(endpoint);
            log.info("OpenCode started and reachable at {}:{}", endpoint.host(), endpoint.port());
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to start OpenCode process. Verify app.opencode.command and app.opencode.args.",
                e
            );
        }
    }

    @PreDestroy
    public synchronized void stopManagedProcess() {
        if (!properties.isStopOnShutdown()) {
            return;
        }

        if (managedProcess == null || !managedProcess.isAlive()) {
            return;
        }

        log.info("Stopping OpenCode process started by Base66");
        managedProcess.toHandle().descendants().forEach(handle -> {
            try {
                handle.destroy();
            } catch (Exception ignored) {
                // Best effort cleanup.
            }
        });
        managedProcess.destroy();
        try {
            if (!managedProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                managedProcess.toHandle().descendants().forEach(handle -> {
                    try {
                        handle.destroyForcibly();
                    } catch (Exception ignored) {
                        // Best effort cleanup.
                    }
                });
                managedProcess.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            managedProcess.toHandle().descendants().forEach(handle -> {
                try {
                    handle.destroyForcibly();
                } catch (Exception ignored) {
                    // Best effort cleanup.
                }
            });
            managedProcess.destroyForcibly();
        }
    }

    private void waitForReachability(Endpoint endpoint) {
        long timeoutMillis = Math.max(1, properties.getStartupTimeoutSeconds()) * 1000L;
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {
            if (managedProcess != null && !managedProcess.isAlive()) {
                throw new IllegalStateException("OpenCode process exited before becoming reachable");
            }

            if (isReachable(endpoint, Duration.ofMillis(500))) {
                return;
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for OpenCode startup", e);
            }
        }

        throw new IllegalStateException(
            "Timed out waiting for OpenCode at " + endpoint.host() + ":" + endpoint.port()
                + " after " + Math.max(1, properties.getStartupTimeoutSeconds()) + " seconds"
        );
    }

    private boolean isReachable(Endpoint endpoint, Duration timeout) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), (int) timeout.toMillis());
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private List<String> buildCommand() {
        String command = Objects.requireNonNull(properties.getCommand(), "app.opencode.command is required").trim();
        if (command.isEmpty()) {
            throw new IllegalStateException("app.opencode.command must not be blank");
        }

        List<String> parts = new ArrayList<>();
        parts.add(resolveCommandPath(command));
        parts.addAll(splitArgs(properties.getArgs()));
        return parts;
    }

    private String resolveCommandPath(String command) {
        if (command.contains("/") || command.contains("\\")) {
            return command;
        }

        String pathValue = System.getenv("PATH");
        if (!StringUtils.hasText(pathValue)) {
            return command;
        }

        List<String> candidates = new ArrayList<>();
        if (isWindows() && !command.contains(".")) {
            candidates.add(command + ".cmd");
            candidates.add(command + ".bat");
            candidates.add(command + ".exe");
            candidates.add(command + ".com");
        }
        candidates.add(command);

        for (String dir : Arrays.stream(pathValue.split(java.io.File.pathSeparator)).map(String::trim).toList()) {
            if (dir.isEmpty()) {
                continue;
            }

            for (String candidate : candidates) {
                Path candidatePath = Paths.get(dir).resolve(candidate);
                if (Files.isRegularFile(candidatePath)) {
                    return candidatePath.toAbsolutePath().normalize().toString();
                }
            }
        }

        return command;
    }

    private List<String> splitArgs(String rawArgs) {
        List<String> tokens = new ArrayList<>();
        if (!StringUtils.hasText(rawArgs)) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < rawArgs.length(); i++) {
            char ch = rawArgs.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(ch) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(ch);
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private Endpoint parseEndpoint(String value) {
        String trimmed = Objects.requireNonNull(value, "opencode.base-url is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("opencode.base-url must not be blank");
        }

        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            int port = uri.getPort();
            if (!StringUtils.hasText(host)) {
                throw new IllegalStateException("opencode.base-url must include host and port, got: " + trimmed);
            }

            if (port <= 0) {
                port = switch ((uri.getScheme() == null ? "" : uri.getScheme().toLowerCase())) {
                    case "http" -> 80;
                    case "https" -> 443;
                    default -> -1;
                };
            }

            if (port <= 0) {
                throw new IllegalStateException("opencode.base-url must include host and port, got: " + trimmed);
            }
            return new Endpoint(host, port);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid opencode.base-url: " + trimmed, e);
        }
    }

    private void startLogPump(Process process) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[opencode] {}", line);
                }
            } catch (IOException e) {
                log.debug("Stopped reading OpenCode process output", e);
            }
        }, "opencode-process-log");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record Endpoint(String host, int port) {
    }
}