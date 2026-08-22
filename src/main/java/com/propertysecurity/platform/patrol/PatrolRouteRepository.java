package com.propertysecurity.platform.patrol;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatrolRouteRepository extends JpaRepository<PatrolRoute, Long> {

    Optional<PatrolRoute> findByIdAndDeletedAtIsNull(Long id);

    List<PatrolRoute> findAllByProperty_IdAndDeletedAtIsNull(Long propertyId);
}
