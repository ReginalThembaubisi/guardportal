package com.propertysecurity.platform.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Keeps idempotency_key from growing indefinitely. 7-day retention covers
 * a guard who is offline over a weekend and still has keys in flight on
 * Monday (24-hour retention would turn that into a silent duplicate on sync).
 *
 * The guard-pwa client itself expires a held write at 72 hours with a
 * "never sent" visible state, so writes older than 7 days have either been
 * processed, replayed, or abandoned by the sender.
 *
 * @Scheduled requires @EnableScheduling — already on the main application
 * class or a config bean; no new annotation needed here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyCleanupService {

    private final IdempotencyService idempotencyService;

    @Scheduled(cron = "0 30 2 * * *")
    public void purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        idempotencyService.deleteExpired(cutoff);
        log.info("Idempotency key cleanup: deleted rows older than {}", cutoff);
    }
}
