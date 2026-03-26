package com.akatsuki.base66.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Service
public class Base66ChatService {

    private static final int LOG_PREVIEW_MAX = 2000;

    private final ChatClient chatClient;

    public Base66ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @SuppressWarnings("null")
    public String chat(String message) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message is required");
        }

        String safeMessage = Objects.requireNonNull(message, "message is required").trim();
        log.info("User -> OpenCode: {}", previewForLog(safeMessage));
        log.info("Sending message to OpenCode. length={}", safeMessage.length());
        String response = chatClient.prompt()
            .user(safeMessage)
            .call()
            .content();
        log.info("OpenCode -> User: {}", previewForLog(response));
        log.info("Received response from OpenCode. length={}", response == null ? 0 : response.length());
        return response;
    }

    private String previewForLog(String text) {
        if (text == null) {
            return "(null)";
        }

        String normalized = text.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= LOG_PREVIEW_MAX) {
            return normalized;
        }

        return normalized.substring(0, LOG_PREVIEW_MAX) + "...(truncated)";
    }
}