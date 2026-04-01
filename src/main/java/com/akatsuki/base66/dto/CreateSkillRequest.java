package com.akatsuki.base66.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSkillRequest(
    @NotBlank(message = "skillName is required")
    String skillName,

    @NotBlank(message = "description is required")
    String description,

    @NotBlank(message = "content is required")
    String content
) {
}
