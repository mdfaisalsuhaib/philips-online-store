package com.philips.onlinestore.dto;

import com.philips.onlinestore.entity.UserRole;

import java.time.Instant;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMs,
        Long id,
        String fullName,
        String email,
        UserRole role,
        boolean enabled,
        Instant createdAt
) {
}
