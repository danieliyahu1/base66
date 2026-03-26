package com.akatsuki.base66.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSkillFromTextRequest(
    @NotBlank(message = "skillName is required") String skillName,
    @NotBlank(message = "content is required") String content
) {
}
