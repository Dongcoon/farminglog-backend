package com.farmlog.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String displayName,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
