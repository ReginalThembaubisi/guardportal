package com.propertysecurity.platform.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdemKey(String idemKey);

    /**
     * Conditional UPDATE: only succeeds (returns 1) if the row is still in_flight
     * and older than the given cutoff. Exactly one thread wins this race — the one
     * that gets rows_affected = 1 owns the reclaim. Setting created_at = NOW()
     * restarts the 120-second staleness window so a second crash won't
     * reclaim mid-processing.
     *
     * The cutoff is passed from Java (LocalDateTime.now().minusSeconds(120)) to
     * avoid dialect-specific INTERVAL syntax in the query body — both H2 (test)
     * and MySQL (production) understand a plain timestamp comparison.
     */
    @Modifying
    @Query(value = "UPDATE idempotency_key SET created_at = NOW() WHERE idem_key = :idemKey AND in_flight = TRUE AND created_at < :cutoff", nativeQuery = true)
    int reclaimStale(@Param("idemKey") String idemKey, @Param("cutoff") java.time.LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE IdempotencyKey k SET k.inFlight = false, k.statusCode = :statusCode, k.responseBody = :responseBody WHERE k.idemKey = :idemKey")
    void finalizeKey(@Param("idemKey") String idemKey, @Param("statusCode") int statusCode, @Param("responseBody") String responseBody);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.idemKey = :idemKey")
    void deleteByIdemKey(@Param("idemKey") String idemKey);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :cutoff")
    void deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
