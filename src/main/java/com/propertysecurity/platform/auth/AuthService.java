package com.propertysecurity.platform.auth;

import com.propertysecurity.platform.auth.dto.AuthResponse;
import com.propertysecurity.platform.security.JwtService;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Email/password login, used by every non-resident role (guard, supervisor, property manager, client, admin). */
    public AuthResponse login(String email, String rawPassword) {
        AppUser user = appUserRepository.findByEmailAndDeletedAtIsNull(email)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return issueToken(user);
    }

    public AuthResponse issueToken(AppUser user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail() != null ? user.getEmail() : user.getPhoneNumber(), user.getRoles());
        return new AuthResponse(token, user.getId(), user.getFullName(), user.getRoles());
    }
}
