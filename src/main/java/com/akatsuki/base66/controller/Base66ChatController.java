package com.akatsuki.base66.controller;

import com.akatsuki.base66.dto.ChatRequest;
import com.akatsuki.base66.dto.ChatResponse;
import com.akatsuki.base66.service.Base66ChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/base66")
public class Base66ChatController {

    private final Base66ChatService base66ChatService;

    public Base66ChatController(Base66ChatService base66ChatService) {
        this.base66ChatService = base66ChatService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        log.info("POST /api/base66/chat request received");
        String content = base66ChatService.chat(request.message());
        log.debug("Chat response generated, length={}", content == null ? 0 : content.length());
        return new ChatResponse(content);
    }
}