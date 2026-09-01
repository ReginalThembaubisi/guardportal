package com.propertysecurity.platform.guard;

import com.propertysecurity.platform.common.BaseEntity;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "guard")
@Getter
@Setter
@NoArgsConstructor
public class Guard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "badge_number", length = 30)
    private String badgeNumber;

    @Column(name = "psira_number", length = 20)
    private String psiraNumber;

    @Column(name = "psira_grade", length = 5)
    private String psiraGrade;

    @Column(name = "psira_expiry")
    private LocalDate psiraExpiry;
}
