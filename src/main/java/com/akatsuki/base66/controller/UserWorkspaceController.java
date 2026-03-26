package com.akatsuki.base66.controller;

import com.akatsuki.base66.dto.CreateSkillFromTextRequest;
import com.akatsuki.base66.dto.CreateSkillFromTextResponse;
import com.akatsuki.base66.service.Base66ChatService;
import com.akatsuki.base66.service.UserWorkspaceService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/base66/skills")
public class UserWorkspaceController {

    private final Base66ChatService base66ChatService;
    private final UserWorkspaceService userWorkspaceService;

    public UserWorkspaceController(Base66ChatService base66ChatService, UserWorkspaceService userWorkspaceService) {
        this.base66ChatService = base66ChatService;
        this.userWorkspaceService = userWorkspaceService;
    }

    @PostMapping("/from-text")
    public CreateSkillFromTextResponse createFromText(@Valid @RequestBody CreateSkillFromTextRequest request) {
        String username = resolveAuthenticatedUsername();
        log.info("Incoming skill creation request from-text. user={}", username);
        String prompt = userWorkspaceService.buildSkillCreationPrompt(request.skillName(), request.content());
        String modelResponse = base66ChatService.chat(prompt);
        return userWorkspaceService.verifySkillCreationResult(username, request.skillName(), modelResponse);
    }

    private String resolveAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated username is required");
        }
        return authentication.getName();
    }
}
