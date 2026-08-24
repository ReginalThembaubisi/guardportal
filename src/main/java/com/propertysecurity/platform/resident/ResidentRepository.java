package com.propertysecurity.platform.resident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    // JOIN FETCH so callers can read user/unit/property fields after the
    // transaction ends (open-in-view is disabled) — same idiom as
    // PropertyManagerRepository/PropertyClientRepository.
    @Query("select r from Resident r join fetch r.user join fetch r.unit u join fetch u.property where r.deletedAt is null")
    List<Resident> findAllByDeletedAtIsNull();

    /** Scoped listing for a property manager or client — only residents on properties they're linked to. */
    @Query("select r from Resident r join fetch r.user join fetch r.unit u join fetch u.property where u.property.id in :propertyIds and r.deletedAt is null")
    List<Resident> findAllByUnit_Property_IdInAndDeletedAtIsNull(@Param("propertyIds") List<Long> propertyIds);

    @Query("select r from Resident r join fetch r.user join fetch r.unit u join fetch u.property where r.id = :id and r.deletedAt is null")
    Optional<Resident> findByIdFetchUnitAndProperty(@Param("id") Long id);

    Optional<Resident> findByUser_IdAndDeletedAtIsNull(Long userId);
}
