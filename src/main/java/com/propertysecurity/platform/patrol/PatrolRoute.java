package com.propertysecurity.platform.patrol;

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

@Entity
@Table(name = "patrol_route")
@Getter
@Setter
@NoArgsConstructor
public class PatrolRoute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "name", nullable = false, length = 150)
    private String name;
}
