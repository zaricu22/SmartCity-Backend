package com.example.smartcityback.asset.infrastructure.persistence.entity;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.infrastructure.persistence.embedded.EnergyEmbeddable;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.UUID;

@Entity
@Table(name = "energy_device", schema = "public", catalog = "smartcity")
public class EnergyDeviceJpaEntity {

    @Id
    @JdbcTypeCode(java.sql.Types.CHAR)  // MySQL does not have a native UUID type, so we store it as CHAR(36)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceType type;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="value", column=@Column(name="rated_capacity_value", nullable = false)),
            @AttributeOverride(name="unit", column=@Column(name="rated_capacity_unit", nullable = false))
    })
    private EnergyEmbeddable ratedCapacity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="value", column=@Column(name="production_value", nullable = false)),
            @AttributeOverride(name="unit", column=@Column(name="production_unit", nullable = false))
    })
    private EnergyEmbeddable production;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private PublicBuildingJpaEntity building;

    public EnergyDeviceJpaEntity() {
        // JPA requires
    }

    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DeviceType getType() { return type; }
    public void setType(DeviceType type) { this.type = type; }

    public EnergyEmbeddable getRatedCapacity() { return ratedCapacity; }
    public void setRatedCapacity(EnergyEmbeddable ratedCapacity) { this.ratedCapacity = ratedCapacity; }

    public EnergyEmbeddable getProduction() { return production; }
    public void setProduction(EnergyEmbeddable production) { this.production = production; }

    public PublicBuildingJpaEntity getBuilding() {
        return building;
    }
    public void setBuilding(PublicBuildingJpaEntity building) {
        this.building = building;
    }

}
