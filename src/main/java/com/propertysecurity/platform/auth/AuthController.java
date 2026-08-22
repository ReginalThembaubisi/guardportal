package com.propertysecurity.platform.auth;

import com.propertysecurity.platform.auth.dto.AuthResponse;
import com.propertysecurity.platform.auth.dto.LoginRequest;
import com.propertysecurity.platform.auth.dto.OtpRequestRequest;
import com.propertysecurity.platform.auth.dto.OtpRequestResponse;
import com.propertysecurity.platform.auth.dto.OtpVerifyRequest;
import com.propertysecurity.platform.config.AppProperties;
import com.propertysecurity.platform.user.AppUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;
    private final AppProperties appProperties;

    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequestRequest request) {
        OtpService.OtpIssued issued = otpService.requestOtp(request.phoneNumber());
        String devOnlyCode = appProperties.getOtp().isExposeCodeInResponse() ? issued.rawCode() : null;
        return new OtpRequestResponse("Code sent", issued.expiresInMinutes(), devOnlyCode);
    }

    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AppUser user = otpService.verifyOtp(request.phoneNumber(), request.code());
        return authService.issueToken(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }
}
