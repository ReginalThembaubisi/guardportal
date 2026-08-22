package com.propertysecurity.platform.vehicle;

import com.propertysecurity.platform.resident.Resident;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resident_vehicle")
@Getter
@Setter
@NoArgsConstructor
public class ResidentVehicle {

    @EmbeddedId
    private ResidentVehicleId id = new ResidentVehicleId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("residentId")
    @JoinColumn(name = "resident_id")
    private Resident resident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("vehicleId")
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
}
