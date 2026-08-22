package com.propertysecurity.platform.patrol;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PatrolRouteCheckpointRepository extends JpaRepository<PatrolRouteCheckpoint, PatrolRouteCheckpointId> {

    @Query("select prc from PatrolRouteCheckpoint prc " +
            "join fetch prc.checkpoint c " +
            "where prc.route.id = :routeId order by prc.sequenceOrder asc")
    List<PatrolRouteCheckpoint> findAllByRouteIdOrderBySequence(Long routeId);
}
