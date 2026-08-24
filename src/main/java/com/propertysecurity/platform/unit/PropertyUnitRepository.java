package com.propertysecurity.platform.unit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PropertyUnitRepository extends JpaRepository<PropertyUnit, Long> {

    List<PropertyUnit> findAllByProperty_IdAndDeletedAtIsNull(Long propertyId);

    Optional<PropertyUnit> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByProperty_IdAndUnitNumberAndDeletedAtIsNull(Long propertyId, String unitNumber);

    Optional<PropertyUnit> findByProperty_IdAndUnitNumberAndDeletedAtIsNull(Long propertyId, String unitNumber);
}
