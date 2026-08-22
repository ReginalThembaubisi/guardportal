package com.propertysecurity.platform.unit;

import com.propertysecurity.platform.common.BaseEntity;
import com.propertysecurity.platform.property.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the "unit" table from property_security_schema.sql. Named
 * PropertyUnit rather than Unit to avoid a confusingly generic class name.
 */
@Entity
@Table(name = "unit", uniqueConstraints = @UniqueConstraint(columnNames = {"property_id", "unit_number"}))
@Getter
@Setter
@NoArgsConstructor
public class PropertyUnit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "unit_number", nullable = false, length = 30)
    private String unitNumber;
}
