package com.akatsuki.base66.config;

import com.akatsuki.base66.opencode.OpenCodeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatModel openCodeChatModel(@Value("${opencode.base-url}") String baseUrl) {
        log.info("Initializing OpenCode ChatModel with base URL {}", baseUrl);
        return new OpenCodeChatModel(baseUrl);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        log.info("Creating ChatClient bean");
        return ChatClient.builder(chatModel).build();
    }
}