package com.propertysecurity.platform.visitorentry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
