package com.akatsuki.base66.dto;

import jakarta.validation.constraints.NotBlank;

public record PermissionReplyRequest(
    @NotBlank(message = "reply is required") String reply
) {
}
