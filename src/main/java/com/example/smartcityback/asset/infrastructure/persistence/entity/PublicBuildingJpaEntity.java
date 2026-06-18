package com.example.smartcityback.asset.infrastructure.persistence.entity;

import com.example.smartcityback.asset.infrastructure.persistence.embedded.EnergyEmbeddable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "public_building", schema = "public", catalog = "smartcity")
public class PublicBuildingJpaEntity {
    @Id
    @JdbcTypeCode(java.sql.Types.CHAR)  // MySQL does not have a native UUID type, so we store it as CHAR(36)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Version
    // Long (nullable wrapper) rather than primitive long is intentional: Hibernate uses null on
    // the version field to detect a brand-new entity and skips the optimistic-lock WHERE version=?
    // check on the very first INSERT. With primitive long (default value 0), Hibernate would treat
    // every new entity as "version 0 already persisted" and attempt an UPDATE instead of INSERT,
    // or produce a spurious OptimisticLockException. The @NotNull constraint is applied by Bean
    // Validation after Hibernate has already initialised the version to 0 on the first flush.
    @NotNull
    private Long version;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="value", column=@Column(name="consumption_value", nullable = false)),
            @AttributeOverride(name="unit", column=@Column(name="consumption_unit", nullable = false))
    })
    private EnergyEmbeddable consumption;

    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EnergyDeviceJpaEntity> devices;

    // equals() and hashCode() are intentionally not overridden.
    // JPA entities use default Java object-identity equality (same reference = same object).
    // Implementing equals() based on the database ID is unreliable because the ID field is
    // null before the first persist — an entity added to a HashSet before saving would be
    // unfindable after saving because its hash code would change. Hibernate guarantees that
    // within a single session the same database row always returns the same Java instance,
    // so reference equality is safe and correct for this use case.
    public PublicBuildingJpaEntity() {
        // JPA requires
    }

    public PublicBuildingJpaEntity(UUID id, String name, String location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public EnergyEmbeddable getConsumption() { return consumption; }
    public void setConsumption(EnergyEmbeddable consumption) { this.consumption = consumption; }

    public List<EnergyDeviceJpaEntity> getDevices() { return devices; }
    public void setDevices(List<EnergyDeviceJpaEntity> devices) { this.devices = devices; }
}
