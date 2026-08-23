package com.propertysecurity.platform.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    // JOIN FETCH through to reportedByGuard.user — IncidentResponse.from needs
    // the guard's name, and open-in-view is disabled, so this avoids both
    // LazyInitializationException and N+1 across a list of incidents.
    @Query("select i from Incident i join fetch i.property join fetch i.reportedByGuard g join fetch g.user " +
            "where i.property.id = :propertyId order by i.reportedAt desc")
    List<Incident> findAllByProperty_IdFetchDetails(@Param("propertyId") Long propertyId);

    @Query("select i from Incident i join fetch i.property join fetch i.reportedByGuard g join fetch g.user where i.id = :id")
    Optional<Incident> findByIdFetchDetails(@Param("id") Long id);
}
