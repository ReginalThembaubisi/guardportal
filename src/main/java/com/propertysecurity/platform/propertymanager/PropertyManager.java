package com.propertysecurity.platform.propertymanager;

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

/** Links a PROPERTY_MANAGER-role app_user to a property. One user can have several rows (several properties). */
@Entity
@Table(name = "property_manager")
@Getter
@Setter
@NoArgsConstructor
public class PropertyManager extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
}
