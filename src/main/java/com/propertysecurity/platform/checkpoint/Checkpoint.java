package com.propertysecurity.platform.checkpoint;

import com.propertysecurity.platform.common.BaseEntity;
import com.propertysecurity.platform.property.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Config/master data (soft-deletable), unlike the append-only checkpoint_scan
 * log — a checkpoint is set up once and patrolled against many times.
 */
@Entity
@Table(name = "checkpoint")
@Getter
@Setter
@NoArgsConstructor
public class Checkpoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    /** Nullable — falls back to property.geoToleranceMeters, then the global default. */
    @Column(name = "geo_tolerance_meters")
    private Integer geoToleranceMeters;
}
