package com.propertysecurity.platform.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Each method runs in its own committed transaction (REQUIRES_NEW). This
 * matters for two reasons:
 *
 *   1. The filter is not itself @Transactional, so there is no ambient TX
 *      for these calls to join. REQUIRES_NEW is still specified explicitly
 *      to prevent a test framework's outer @Transactional from absorbing the
 *      writes and leaving them uncommitted for the duration of a test.
 *
 *   2. DataIntegrityViolationException from insert() must propagate OUTSIDE
 *      the @Transactional boundary so the EntityManager is not left in a
 *      dirty state. Catching it inside the @Transactional method (where the
 *      EntityManager has already seen the flush failure) would corrupt the
 *      session — the filter catches it after the REQUIRES_NEW transaction has
 *      already rolled back cleanly.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repo;

    /**
     * Attempts to INSERT a new in-flight key row. Uses saveAndFlush to force
     * the constraint check immediately, within this REQUIRES_NEW transaction.
     * If the unique constraint on idem_key fires, DataIntegrityViolationException
     * propagates to the caller — never caught here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKey insert(String idemKey, String endpoint, Long principalId) {
        IdempotencyKey key = new IdempotencyKey();
        key.setIdemKey(idemKey);
        key.setEndpoint(endpoint);
        key.setPrincipalId(principalId);
        key.setInFlight(true);
        return repo.saveAndFlush(key);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyKey> findByKey(String idemKey) {
        return repo.findByIdemKey(idemKey);
    }

    /** Returns true iff this thread won the race to reclaim a stale in-flight row. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryReclaim(String idemKey) {
        return repo.reclaimStale(idemKey, java.time.LocalDateTime.now().minusSeconds(120)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalize(String idemKey, int statusCode, String responseBody) {
        repo.finalizeKey(idemKey, statusCode, responseBody);
    }

    /** Called when the response should not be cached (5xx, 401, 403, etc.). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(String idemKey) {
        repo.deleteByIdemKey(idemKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteExpired(LocalDateTime cutoff) {
        repo.deleteByCreatedAtBefore(cutoff);
    }
}
