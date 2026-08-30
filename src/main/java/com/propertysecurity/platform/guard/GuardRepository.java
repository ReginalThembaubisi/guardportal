package com.propertysecurity.platform.guard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuardRepository extends JpaRepository<Guard, Long> {

    // Fetch-joined: GuardResponse.from() reads both guard.user and
    // guard.property, and open-in-view is disabled, so these would otherwise
    // throw LazyInitializationException once the transaction closes.
    @Query("select g from Guard g join fetch g.user join fetch g.property where g.deletedAt is null")
    List<Guard> findAllByDeletedAtIsNull();

    @Query("select g from Guard g join fetch g.user join fetch g.property where g.id = :id and g.deletedAt is null")
    Optional<Guard> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    Optional<Guard> findByUser_IdAndDeletedAtIsNull(Long userId);

    Optional<Guard> findByUser_PhoneNumberAndDeletedAtIsNull(String phoneNumber);

    // JOIN FETCH — for callers (like IncidentService) that need guard.user
    // still readable after the transaction ends (open-in-view disabled).
    @Query("select g from Guard g join fetch g.user where g.user.id = :userId and g.deletedAt is null")
    Optional<Guard> findByUser_IdAndDeletedAtIsNullFetchUser(@Param("userId") Long userId);
}
