package com.akatsuki.base66.opencode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class OpenCodeChatModel implements ChatModel {

    private final WebClient webClient;
    private String sessionId;

    public OpenCodeChatModel(String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        log.info("OpenCodeChatModel created for baseUrl={}", baseUrl);
        initSession();
    }

    private void initSession() {
        // Create the session synchronously on startup
        log.info("Initializing OpenCode session");
        Map<?, ?> response = webClient.post()
                .uri("/session")
                .bodyValue(Map.of("title", "Spring AI Integration"))
                .retrieve()
                .bodyToMono(Map.class)
                .block(); // Blocking is okay here during initialization

        if (response != null && response.containsKey("id")) {
            this.sessionId = response.get("id").toString();
            log.info("OpenCode session initialized with id={}", this.sessionId);
        }
        else {
            log.warn("OpenCode session response did not include an id");
        }
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // Fallback standard call using WebClient synchronously
        return stream(prompt).blockLast();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        String userText = prompt.getContents();
        log.info("Streaming chat request to OpenCode. length={}", userText == null ? 0 : userText.length());
        var requestPayload = new OpenCodePromptRequest(List.of(new TextPartInput(userText)));

        return webClient.post()
                .uri("/session/{id}/message", this.sessionId)
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

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().build();
    }
}