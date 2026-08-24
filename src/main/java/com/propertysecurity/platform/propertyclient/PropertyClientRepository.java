package com.propertysecurity.platform.propertyclient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PropertyClientRepository extends JpaRepository<PropertyClient, Long> {

    boolean existsByUser_IdAndProperty_IdAndDeletedAtIsNull(Long userId, Long propertyId);

    boolean existsByUser_IdAndDeletedAtIsNull(Long userId);

    // JOIN FETCH so callers can read Property fields after the transaction ends
    // (open-in-view is disabled) — see PropertyManagerRepository for why a plain
    // derived query returning lazy Property proxies isn't safe here.
    @Query("select pc from PropertyClient pc join fetch pc.property where pc.user.id = :userId and pc.deletedAt is null")
    List<PropertyClient> findAllByUser_IdAndDeletedAtIsNull(@Param("userId") Long userId);
}
