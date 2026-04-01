package com.akatsuki.base66.controller;

import com.akatsuki.base66.dto.CreateSkillFromTextRequest;
import com.akatsuki.base66.dto.CreateSkillFromTextResponse;
import com.akatsuki.base66.dto.SkillDetailResponse;
import com.akatsuki.base66.dto.SkillSummaryResponse;
import com.akatsuki.base66.dto.UpdateSkillRequest;
import com.akatsuki.base66.service.Base66ChatService;
import com.akatsuki.base66.service.UserWorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping
    public List<SkillSummaryResponse> list() {
        String username = resolveAuthenticatedUsername();
        log.info("Skill list requested by user='{}'", username);
        List<SkillSummaryResponse> skills = userWorkspaceService.listSkills(username);
        log.info("Skill list returned {} skills for user='{}'", skills.size(), username);
        return skills;
    }

    @GetMapping("/{skillName}")
    public SkillDetailResponse get(@PathVariable String skillName) {
        String username = resolveAuthenticatedUsername();
        log.info("Skill detail requested by user='{}' skill='{}'", username, skillName);
        return userWorkspaceService.getSkillContent(username, skillName);
    }

    @PutMapping("/{skillName}")
    public SkillDetailResponse update(@PathVariable String skillName,
                                      @Valid @RequestBody UpdateSkillRequest request) {
        String username = resolveAuthenticatedUsername();
        log.info("Skill update requested by user='{}' skill='{}'", username, skillName);
        return userWorkspaceService.updateSkill(username, skillName, request.description(), request.content());
    }

    @PostMapping("/from-text")
    public CreateSkillFromTextResponse createFromText(@Valid @RequestBody CreateSkillFromTextRequest request) {
        String username = resolveAuthenticatedUsername();
        log.info("Incoming skill creation request from-text. user={}", username);
        String prompt = userWorkspaceService.buildSkillCreationPrompt(request.skillName(), request.content());
        String modelResponse = base66ChatService.chat(prompt);
        return userWorkspaceService.verifySkillCreationResult(username, request.skillName(), modelResponse);
    }

    @DeleteMapping("/{skillName}")
    public Map<String, Boolean> delete(@PathVariable String skillName) {
        String username = resolveAuthenticatedUsername();
        log.info("Skill delete requested by user='{}' skill='{}'", username, skillName);
        boolean success = userWorkspaceService.deleteSkill(username, skillName);
        return Map.of("success", success);
    }

    private String resolveAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated username is required");
        }
        return authentication.getName();
    }
}
