package com.propertysecurity.platform.auth.dto;

import com.propertysecurity.platform.user.Role;

import java.util.Set;

public record AuthResponse(
        String token,
        Long userId,
        String fullName,
        Set<Role> roles
) {
}
