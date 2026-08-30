package com.propertysecurity.platform.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByQrToken(String qrToken);

    List<Invitation> findAllByResident_IdOrderByCreatedAtDesc(Long residentId);

    Optional<Invitation> findByIdAndResident_Id(Long id, Long residentId);

    /**
     * Short codes are reused across time once an invitation is no longer
     * PENDING-and-overlapping (see V13), so more than one row at a property
     * can share a code. The most recently created one is what a guard means
     * by a code presented today — this is how check-in resolves a short
     * code to a single invitation.
     */
    Optional<Invitation> findTopByShortCodeAndResident_Unit_Property_IdOrderByCreatedAtDesc(String shortCode, Long propertyId);

    /** Backs the collision check in InvitationService's short-code generation. */
    @Query("""
            select count(i) > 0 from Invitation i
            where i.shortCode = :shortCode
              and i.status = com.propertysecurity.platform.invitation.InvitationStatus.PENDING
              and i.resident.unit.property.id = :propertyId
              and i.validFrom < :validUntil
              and i.validUntil > :validFrom
            """)
    boolean existsOverlappingPendingShortCode(@Param("shortCode") String shortCode, @Param("propertyId") Long propertyId,
            @Param("validFrom") LocalDateTime validFrom, @Param("validUntil") LocalDateTime validUntil);
}
