package com.example.smartcityback.asset.infrastructure.persistence.repository;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import com.example.smartcityback.asset.infrastructure.persistence.implementation.PublicBuildingRepositoryImpl;
import com.example.smartcityback.asset.shared.PagedResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// Each test is executed in transaction and rolled back after execution to ensure test isolation.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(PublicBuildingRepositoryImpl.class)
class PublicBuildingRepositoryImplTest {

    // Testcontainer as real temporary instance of PostgreSQL. The container will be shared across all tests in this class.
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("smartcity_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PublicBuildingRepositoryImpl repository;

    @Autowired
    private TestEntityManager em;

    private static final UUID   BUILDING_ID     = UUID.randomUUID();
    private static final UUID   DEVICE_ID       = UUID.randomUUID();
    private static final Energy CAPACITY_100_KW = new Energy(new BigDecimal("100"), EnergyUnit.kW);

    // =====================================================================
    // save / findById
    // =====================================================================

    @Test
    void save_and_findById_returnsBuilding() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        repository.save(building);
        em.flush();
        em.clear();

        // Flush + clear is important to ensure we are actually fetching from the DB
        Optional<PublicBuilding> found = repository.findById(BUILDING_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(BUILDING_ID);
        assertThat(found.get().getName()).isEqualTo("City Hall");
        assertThat(found.get().getLocation()).isEqualTo("Main St 1");
        assertThat(found.get().getDevices()).isEmpty();
    }

    @Test
    void save_withDevice_persistsDevice() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW));

        repository.save(building);
        em.flush();
        em.clear();

        Optional<PublicBuilding> found = repository.findById(BUILDING_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getDevices()).hasSize(1);
        assertThat(found.get().getDevices().get(0).getType()).isEqualTo(DeviceType.SOLAR);
    }

    @Test
    void save_withMultipleDevices_persistsAll() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), DeviceType.SOLAR,   CAPACITY_100_KW));
        building.addDevice(new EnergyDevice(UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW));

        repository.save(building);
        em.flush();
        em.clear();

        assertThat(repository.findById(BUILDING_ID).orElseThrow().getDevices()).hasSize(2);
    }

    // =====================================================================
    // findById — not found
    // =====================================================================

    @Test
    void findById_nonExistentId_returnsEmpty() {
        Optional<PublicBuilding> found = repository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    // =====================================================================
    // delete
    // =====================================================================

    @Test
    void delete_existingBuilding_removesIt() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        repository.save(building);
        em.flush();
        em.clear();

        repository.delete(BUILDING_ID);
        em.flush();

        assertThat(repository.findById(BUILDING_ID)).isEmpty();
    }

    @Test
    void delete_buildingWithDevices_cascadesDeviceRemoval() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW));
        repository.save(building);
        em.flush();
        em.clear();

        repository.delete(BUILDING_ID);
        em.flush();

        assertThat(repository.findById(BUILDING_ID)).isEmpty();
        // Cascade ALL + orphanRemoval ensures the device row is also removed.
        assertThat(em.find(
                com.example.smartcityback.asset.infrastructure.persistence.entity.EnergyDeviceJpaEntity.class,
                DEVICE_ID
        )).isNull();
    }

    // =====================================================================
    // production persistence
    // =====================================================================

    @Test
    void save_withChangedProduction_persistsProduction() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice device = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW);
        building.addDevice(device);
        building.changeDeviceProduction(DEVICE_ID, new Energy(new BigDecimal("60"), EnergyUnit.kW));

        repository.save(building);
        em.flush();
        em.clear();

        PublicBuilding found = repository.findById(BUILDING_ID).orElseThrow();
        Energy production = found.getDevices().get(0).getProductionRate();

        assertThat(production.value()).isEqualByComparingTo("60");
        assertThat(production.unit()).isEqualTo(EnergyUnit.kW);
    }

    // =====================================================================
    // findAll — pagination
    // =====================================================================

    @Test
    void findAll_returnsCorrectPaginationFields() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(0, 2, "name", "asc");

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(3L);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.pageNumber()).isEqualTo(0);
        assertThat(result.pageSize()).isEqualTo(2);
    }

    @Test
    void findAll_secondPage_returnsCorrectPageNumberAndRemainingItems() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(1, 2, "name", "asc");

        assertThat(result.content()).hasSize(1);
        assertThat(result.pageNumber()).isEqualTo(1);
    }

    @Test
    void findAll_sortAsc_returnsItemsInAscendingOrder() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(0, 10, "name", "asc");

        assertThat(result.content())
                .extracting(PublicBuilding::getName)
                .containsExactly("Alpha", "Beta", "Gamma");
    }

    @Test
    void findAll_sortDesc_returnsItemsInDescendingOrder() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(0, 10, "name", "desc");

        assertThat(result.content())
                .extracting(PublicBuilding::getName)
                .containsExactly("Gamma", "Beta", "Alpha");
    }

    // =====================================================================
    // consumption persistence
    // =====================================================================

    @Test
    void save_withChangedConsumption_persistsConsumption() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, DeviceType.BATTERY, CAPACITY_100_KW));
        building.changeConsumption(new Energy(new BigDecimal("50"), EnergyUnit.kW));

        repository.save(building);
        em.flush();
        em.clear();

        PublicBuilding found = repository.findById(BUILDING_ID).orElseThrow();

        assertThat(found.getConsumption().value()).isEqualByComparingTo("50");
        assertThat(found.getConsumption().unit()).isEqualTo(EnergyUnit.kW);
    }
}
