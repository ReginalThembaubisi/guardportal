package com.propertysecurity.platform.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findAllByDeletedAtIsNull();

    Optional<Property> findByIdAndDeletedAtIsNull(Long id);
}
