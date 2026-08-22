package com.propertysecurity.platform.property;

import com.propertysecurity.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "property")
@Getter
@Setter
@NoArgsConstructor
public class Property extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Africa/Johannesburg";

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /** Nullable — falls back to app.geo.default-tolerance-meters when unset. See ShiftService. */
    @Column(name = "geo_tolerance_meters")
    private Integer geoToleranceMeters;
}
