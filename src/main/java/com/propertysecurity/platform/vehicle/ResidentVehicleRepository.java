package com.propertysecurity.platform.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResidentVehicleRepository extends JpaRepository<ResidentVehicle, ResidentVehicleId> {

    boolean existsByVehicle_Id(Long vehicleId);

    boolean existsByResident_IdAndVehicle_Id(Long residentId, Long vehicleId);

    // JOIN FETCH so the caller can read the linked Vehicle's fields after this
    // read-only transaction ends (open-in-view is disabled) — a plain derived
    // query returns lazy Vehicle proxies that throw LazyInitializationException
    // the moment the controller (outside the transaction) touches them.
    @Query("select rv from ResidentVehicle rv join fetch rv.vehicle where rv.resident.id = :residentId")
    List<ResidentVehicle> findAllByResident_Id(@Param("residentId") Long residentId);
}
