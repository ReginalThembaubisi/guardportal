package com.propertysecurity.platform.guard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuardRepository extends JpaRepository<Guard, Long> {

    List<Guard> findAllByDeletedAtIsNull();

    Optional<Guard> findByIdAndDeletedAtIsNull(Long id);

    Optional<Guard> findByUser_IdAndDeletedAtIsNull(Long userId);

    // JOIN FETCH — for callers (like IncidentService) that need guard.user
    // still readable after the transaction ends (open-in-view disabled).
    @Query("select g from Guard g join fetch g.user where g.user.id = :userId and g.deletedAt is null")
    Optional<Guard> findByUser_IdAndDeletedAtIsNullFetchUser(@Param("userId") Long userId);
}
