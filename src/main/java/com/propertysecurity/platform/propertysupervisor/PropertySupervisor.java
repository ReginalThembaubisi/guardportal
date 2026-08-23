package com.propertysecurity.platform.propertysupervisor;

import com.propertysecurity.platform.common.BaseEntity;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Links a SUPERVISOR-role app_user to a property. Mirrors PropertyManager exactly. */
@Entity
@Table(name = "property_supervisor")
@Getter
@Setter
@NoArgsConstructor
public class PropertySupervisor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
}
