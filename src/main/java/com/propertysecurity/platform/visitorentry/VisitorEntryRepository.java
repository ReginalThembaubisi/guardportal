package com.propertysecurity.platform.visitorentry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VisitorEntryRepository extends JpaRepository<VisitorEntry, Long> {

    // LEFT JOIN FETCH so the caller can read the linked Vehicle's fields (if
    // any) after this transaction ends (open-in-view is disabled) — a plain
    // findById returns a lazy Vehicle proxy that throws
    // LazyInitializationException the moment something outside the
    // transaction touches it. See ResidentVehicleRepository for the same
    // pattern.
    @Query("select ve from VisitorEntry ve left join fetch ve.vehicle where ve.id = :id")
    Optional<VisitorEntry> findByIdFetchVehicle(@Param("id") Long id);

    @Query("select ve from VisitorEntry ve left join fetch ve.vehicle where ve.vehicle.registration = :registration order by ve.enteredAt desc")
    List<VisitorEntry> findAllByVehicle_RegistrationOrderByEnteredAtDesc(@Param("registration") String registration);

    @Query("select ve from VisitorEntry ve left join fetch ve.vehicle where ve.vehicle.registration = :registration and ve.property.id = :propertyId order by ve.enteredAt desc")
    List<VisitorEntry> findAllByVehicle_RegistrationAndProperty_IdOrderByEnteredAtDesc(
            @Param("registration") String registration, @Param("propertyId") Long propertyId);

    @Query("select ve from VisitorEntry ve left join fetch ve.vehicle where ve.property.id = :propertyId and ve.exitedAt is null order by ve.enteredAt asc")
    List<VisitorEntry> findAllOnSiteByProperty_Id(@Param("propertyId") Long propertyId);

    @Query("select ve from VisitorEntry ve left join fetch ve.vehicle where ve.vehicle.registration = :registration and ve.property.id in :propertyIds order by ve.enteredAt desc")
    List<VisitorEntry> findAllByVehicle_RegistrationAndProperty_IdInOrderByEnteredAtDesc(
            @Param("registration") String registration, @Param("propertyIds") List<Long> propertyIds);

    // A resident's own visitor history — entries from invitations they personally created.
    @Query("select ve from VisitorEntry ve left join fetch ve.vehicle where ve.invitation.resident.id = :residentId order by ve.enteredAt desc")
    List<VisitorEntry> findAllByInvitation_Resident_IdOrderByEnteredAtDesc(@Param("residentId") Long residentId);

    // Incident-investigation lookup: "who visited on [day/range]" — the
    // actual paper-register equivalent (flip to a date, read the page),
    // which nothing else in the system offers. left join fetch ve.unit too
    // (unlike the other queries here) since the history view displays the
    // unit number directly rather than just an id.
    @Query("select ve from VisitorEntry ve left join fetch ve.vehicle left join fetch ve.unit where ve.property.id = :propertyId and ve.enteredAt >= :from and ve.enteredAt < :to order by ve.enteredAt desc")
    List<VisitorEntry> findAllByProperty_IdAndEnteredAtBetween(
            @Param("propertyId") Long propertyId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
