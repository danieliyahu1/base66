package com.akatsuki.base66.config;

import com.akatsuki.base66.opencode.OpenCodeChatModel;
import com.akatsuki.base66.service.UserWorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Slf4j
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatModel openCodeChatModel(
        @Value("${opencode.base-url}") String baseUrl,
        @Value("${opencode.session-ttl-seconds:1800}") long sessionTtlSeconds,
        UserWorkspaceService userWorkspaceService
    ) {
        log.info("Initializing OpenCode ChatModel with base URL {}", baseUrl);
        return new OpenCodeChatModel(baseUrl, userWorkspaceService, sessionTtlSeconds);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        log.info("Creating ChatClient bean");
        return ChatClient.builder(Objects.requireNonNull(chatModel, "chatModel is required")).build();
    }
}