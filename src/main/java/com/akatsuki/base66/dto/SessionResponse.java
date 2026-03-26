package com.akatsuki.base66.dto;

public record SessionResponse(
    String id,
    String title,
    long createdAt,
    long updatedAt,
    boolean active
) {
}
