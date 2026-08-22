package com.propertysecurity.platform.auth;

import com.propertysecurity.platform.config.AppProperties;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpVerificationRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public record OtpIssued(String rawCode, int expiresInMinutes) {
    }

    public OtpIssued requestOtp(String phoneNumber) {
        AppUser user = appUserRepository.findByPhoneNumberAndDeletedAtIsNull(phoneNumber)
                .filter(u -> u.isActive() && u.getRoles().contains(Role.RESIDENT))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active resident account found for that phone number"));

        String rawCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        int expiryMinutes = appProperties.getOtp().getExpiryMinutes();

        OtpVerification otp = new OtpVerification();
        otp.setPhoneNumber(user.getPhoneNumber());
        otp.setOtpCodeHash(passwordEncoder.encode(rawCode));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        otpVerificationRepository.save(otp);

        // Phase 1 stub: no SMS provider wired up yet, so the code is logged
        // server-side only. See app.otp.expose-code-in-response for local testing.
        log.info("OTP for {} is {} (expires in {} min)", maskPhone(user.getPhoneNumber()), rawCode, expiryMinutes);

        return new OtpIssued(rawCode, expiryMinutes);
    }

    public AppUser verifyOtp(String phoneNumber, String code) {
        OtpVerification otp = otpVerificationRepository
                .findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new BadRequestException("No pending code for this phone number"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Code has expired, request a new one");
        }
        if (otp.getAttemptCount() >= appProperties.getOtp().getMaxAttempts()) {
            throw new BadRequestException("Too many attempts, request a new code");
        }

        if (!passwordEncoder.matches(code, otp.getOtpCodeHash())) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            otpVerificationRepository.save(otp);
            throw new BadRequestException("Invalid code");
        }

        otp.setConsumedAt(LocalDateTime.now());
        otpVerificationRepository.save(otp);

        return appUserRepository.findByPhoneNumberAndDeletedAtIsNull(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber.length() <= 4) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
