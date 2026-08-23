package com.propertysecurity.platform.propertysupervisor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PropertySupervisorRepository extends JpaRepository<PropertySupervisor, Long> {

    boolean existsByUser_IdAndProperty_IdAndDeletedAtIsNull(Long userId, Long propertyId);

    boolean existsByUser_IdAndDeletedAtIsNull(Long userId);

    // JOIN FETCH — same reasoning as PropertyManagerRepository (open-in-view disabled).
    @Query("select ps from PropertySupervisor ps join fetch ps.property where ps.user.id = :userId and ps.deletedAt is null")
    List<PropertySupervisor> findAllByUser_IdAndDeletedAtIsNull(@Param("userId") Long userId);
}
