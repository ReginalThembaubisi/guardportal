package com.propertysecurity.platform.propertymanager;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PropertyManagerRepository extends JpaRepository<PropertyManager, Long> {

    boolean existsByUser_IdAndProperty_IdAndDeletedAtIsNull(Long userId, Long propertyId);

    boolean existsByUser_IdAndDeletedAtIsNull(Long userId);

    // JOIN FETCH so callers can read Property fields after the transaction ends
    // (open-in-view is disabled) — see ResidentVehicleRepository for why a plain
    // derived query returning lazy Property proxies isn't safe here.
    @Query("select pm from PropertyManager pm join fetch pm.property where pm.user.id = :userId and pm.deletedAt is null")
    List<PropertyManager> findAllByUser_IdAndDeletedAtIsNull(@Param("userId") Long userId);
}
