package com.akatsuki.base66.dto;

import java.util.List;

public record PendingPermissionResponse(
    String id,
    String permission,
    List<String> patterns
) {
}
