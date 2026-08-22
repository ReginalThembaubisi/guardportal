package com.propertysecurity.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Otp otp = new Otp();

    @Getter
    @Setter
    public static class Jwt {
        /** HMAC signing secret. Must be at least 256 bits for HS256. */
        private String secret;
        private long expirationMinutes;
    }

    @Getter
    @Setter
    public static class Otp {
        /**
         * Phase 1 stub: no SMS provider wired up yet. When true, the raw OTP
         * code is echoed back in the API response for local/dev testing.
         * Must be false before any real deployment.
         */
        private boolean exposeCodeInResponse;
        private int expiryMinutes;
        private int maxAttempts;
    }
}
