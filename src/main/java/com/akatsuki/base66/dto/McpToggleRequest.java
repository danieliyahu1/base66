package com.akatsuki.base66.dto;

import jakarta.validation.constraints.NotNull;

public record McpToggleRequest(
    @NotNull Boolean enabled
) {
}
