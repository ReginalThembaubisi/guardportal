package com.propertysecurity.platform.user.dto;

import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.Role;

import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
        Long id,
        String fullName,
        String phoneNumber,
        String email,
        Set<Role> roles,
        boolean active,
        LocalDateTime createdAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRoles(),
                user.isActive(),
                user.getCreatedAt());
    }
}
