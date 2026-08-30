package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.exception.TooManyAttemptsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-guard fixed-window limiter on failed short-code attempts.
 * No rate-limiting infrastructure exists anywhere else in this codebase, so
 * this is deliberately minimal: it resets on app restart and doesn't
 * coordinate across instances, both fine for the current single-instance
 * dev/pilot deployment (revisit if that changes).
 *
 * Only failures count. A guard correctly checking in twenty visitors in
 * five minutes is normal and must never be throttled; eight *wrong* codes
 * in five minutes is not, and is still a negligible fraction of the
 * six-digit keyspace even sustained for days, so this is about noticing a
 * token being hammered, not truly preventing brute force by itself.
 */
@Component
public class ShortCodeRateLimiter {

    private static final int MAX_FAILURES_PER_WINDOW = 8;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private record Window(Instant start, int count) {
    }

    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public void assertNotBlocked(Long guardUserId) {
        Window w = windows.get(guardUserId);
        if (w != null && !windowExpired(w) && w.count() >= MAX_FAILURES_PER_WINDOW) {
            throw new TooManyAttemptsException("Too many incorrect codes — wait a few minutes before trying again");
        }
    }

    public void recordFailure(Long guardUserId) {
        windows.compute(guardUserId, (id, w) -> {
            if (w == null || windowExpired(w)) {
                return new Window(Instant.now(), 1);
            }
            return new Window(w.start(), w.count() + 1);
        });
    }

    public void recordSuccess(Long guardUserId) {
        windows.remove(guardUserId);
    }

    private boolean windowExpired(Window w) {
        return Duration.between(w.start(), Instant.now()).compareTo(WINDOW) >= 0;
    }
}
