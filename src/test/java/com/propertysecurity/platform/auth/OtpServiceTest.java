package com.propertysecurity.platform.auth;

import com.propertysecurity.platform.config.AppProperties;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private OtpService otpService;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getOtp().setExpiryMinutes(5);
        appProperties.getOtp().setMaxAttempts(5);
        otpService = new OtpService(otpVerificationRepository, appUserRepository, passwordEncoder, appProperties);
    }

    private AppUser resident() {
        AppUser user = new AppUser();
        user.setPhoneNumber("+27821234567");
        user.setActive(true);
        user.setRoles(Set.of(Role.RESIDENT));
        return user;
    }

    @Test
    void requestOtpRejectsUnknownPhoneNumber() {
        when(appUserRepository.findByPhoneNumberAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.requestOtp("+27000000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyOtpSucceedsWithCorrectCode() {
        when(appUserRepository.findByPhoneNumberAndDeletedAtIsNull("+27821234567"))
                .thenReturn(Optional.of(resident()));
        when(otpVerificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OtpService.OtpIssued issued = otpService.requestOtp("+27821234567");
        assertThat(issued.rawCode()).matches("\\d{6}");

        OtpVerification saved = new OtpVerification();
        saved.setPhoneNumber("+27821234567");
        saved.setOtpCodeHash(passwordEncoder.encode(issued.rawCode()));
        saved.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(otpVerificationRepository.findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc("+27821234567"))
                .thenReturn(Optional.of(saved));

        AppUser result = otpService.verifyOtp("+27821234567", issued.rawCode());
        assertThat(result.getPhoneNumber()).isEqualTo("+27821234567");
        assertThat(saved.getConsumedAt()).isNotNull();
    }

    @Test
    void verifyOtpRejectsWrongCode() {
        OtpVerification saved = new OtpVerification();
        saved.setPhoneNumber("+27821234567");
        saved.setOtpCodeHash(passwordEncoder.encode("111111"));
        saved.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(otpVerificationRepository.findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc("+27821234567"))
                .thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> otpService.verifyOtp("+27821234567", "222222"))
                .isInstanceOf(BadRequestException.class);
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getConsumedAt()).isNull();
    }

    @Test
    void verifyOtpRejectsExpiredCode() {
        OtpVerification saved = new OtpVerification();
        saved.setPhoneNumber("+27821234567");
        saved.setOtpCodeHash(passwordEncoder.encode("111111"));
        saved.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(otpVerificationRepository.findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc("+27821234567"))
                .thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> otpService.verifyOtp("+27821234567", "111111"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyOtpRejectsAfterMaxAttempts() {
        OtpVerification saved = new OtpVerification();
        saved.setPhoneNumber("+27821234567");
        saved.setOtpCodeHash(passwordEncoder.encode("111111"));
        saved.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        saved.setAttemptCount(5);

        when(otpVerificationRepository.findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc("+27821234567"))
                .thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> otpService.verifyOtp("+27821234567", "111111"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many attempts");
    }
}
