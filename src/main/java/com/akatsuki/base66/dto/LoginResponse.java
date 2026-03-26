package com.akatsuki.base66.dto;

public record LoginResponse(String username, String token, String tokenType, String agentName) {
}
