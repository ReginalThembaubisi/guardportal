package com.propertysecurity.platform.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentMediaRepository extends JpaRepository<IncidentMedia, Long> {

    List<IncidentMedia> findAllByIncident_IdOrderByIdAsc(Long incidentId);

    Optional<IncidentMedia> findByIdAndIncident_Id(Long id, Long incidentId);
}
