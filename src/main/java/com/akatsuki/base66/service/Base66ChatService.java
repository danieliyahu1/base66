package com.akatsuki.base66.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class Base66ChatService {

    private final ChatClient chatClient;

    public Base66ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String message) {
        log.info("Sending message to OpenCode. length={}", message == null ? 0 : message.length());
        String response = chatClient.prompt()
            .user(message)
            .call()
            .content();
        log.info("Received response from OpenCode. length={}", response == null ? 0 : response.length());
        return response;
    }
}