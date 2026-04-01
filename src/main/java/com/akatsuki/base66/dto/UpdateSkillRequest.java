package com.akatsuki.base66.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSkillRequest(
    @NotBlank(message = "description is required")
    String description,

    @NotBlank(message = "content is required")
    String content
) {
}
