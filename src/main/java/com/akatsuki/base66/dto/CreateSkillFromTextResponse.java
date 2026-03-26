package com.akatsuki.base66.dto;

public record CreateSkillFromTextResponse(
    boolean success,
    String path,
    String message
) {
}
