package com.propertysecurity.platform.resident;

import com.propertysecurity.platform.common.BaseEntity;
import com.propertysecurity.platform.unit.PropertyUnit;
import com.propertysecurity.platform.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resident")
@Getter
@Setter
@NoArgsConstructor
public class Resident extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private PropertyUnit unit;
}
