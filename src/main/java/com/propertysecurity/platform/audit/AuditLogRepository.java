package com.propertysecurity.platform.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Optional<AuditLog> findTopByOrderByIdDesc();

    List<AuditLog> findAllByOrderByIdAsc();

    List<AuditLog> findByEntityNameAndEntityIdOrderByIdAsc(String entityName, Long entityId);

    @Query("select a from AuditLog a where a.entityName = :entityName and a.entityId in :ids order by a.id asc")
    List<AuditLog> findByEntityNameAndEntityIdInOrderByIdAsc(
            @Param("entityName") String entityName,
            @Param("ids") Collection<Long> ids);
}
