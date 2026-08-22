package com.example.smartcityback.asset.infrastructure.persistence.repository;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.readmodel.PublicBuildingSummary;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import com.example.smartcityback.asset.infrastructure.persistence.implementation.PublicBuildingRepositoryImpl;
import com.example.smartcityback.asset.shared.PagedResult;
import org.junit.jupiter.api.DisplayName;
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

    // Adds a device to the building and immediately sets its production rate to match its
    // capacity — changeConsumption() checks total production rate, not rated capacity, so
    // any test that saves a building with nonzero consumption needs its devices producing.
    private static EnergyDevice addProducingDevice(PublicBuilding building, UUID deviceId, DeviceType type, Energy capacity) {
        EnergyDevice device = new EnergyDevice(deviceId, "Test Device", type, capacity);
        building.addDevice(device);
        building.changeDeviceProduction(deviceId, capacity);
        return device;
    }

    // =====================================================================
    // save / findById
    // =====================================================================

    @Test
    @DisplayName("saves a building with no devices and reads it back correctly through a real Postgres round trip")
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
    @DisplayName("saves a building with one device and reads the device back with its type correctly persisted")
    void save_withDevice_persistsDevice() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        repository.save(building);
        em.flush();
        em.clear();

        Optional<PublicBuilding> found = repository.findById(BUILDING_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getDevices()).hasSize(1);
        assertThat(found.get().getDevices().get(0).getType()).isEqualTo(DeviceType.SOLAR);
    }

    @Test
    @DisplayName("saves a building with two devices of different types and reads both of them back")
    void save_withMultipleDevices_persistsAll() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.BATTERY, CAPACITY_100_KW));

        repository.save(building);
        em.flush();
        em.clear();

        assertThat(repository.findById(BUILDING_ID).orElseThrow().getDevices()).hasSize(2);
    }

    // =====================================================================
    // findById — not found
    // =====================================================================

    @Test
    @DisplayName("returns empty for a building ID that was never saved")
    void findById_nonExistentId_returnsEmpty() {
        Optional<PublicBuilding> found = repository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    // =====================================================================
    // delete
    // =====================================================================

    @Test
    @DisplayName("deletes a building with no devices, and it's gone on the next lookup")
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
    @DisplayName("deletes a building's device row along with the building itself, verified by querying the "
            + "child JPA entity directly instead of trusting the parent lookup alone")
    void delete_buildingWithDevices_cascadesDeviceRemoval() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
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
    @DisplayName("persists a device's production rate change (60 kW) across a save/reload round trip")
    void save_withChangedProduction_persistsProduction() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);
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
    @DisplayName("returns the correct total-element and total-page counts when asking for a page of 2 out of "
            + "3 saved buildings")
    void findAll_returnsCorrectPaginationFields() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(null, null, 0, 2, "name", "asc");

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(3L);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.pageNumber()).isEqualTo(0);
        assertThat(result.pageSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("returns the single remaining building on page 2 when 3 buildings are split across pages of 2")
    void findAll_secondPage_returnsCorrectPageNumberAndRemainingItems() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(null, null, 1, 2, "name", "asc");

        assertThat(result.content()).hasSize(1);
        assertThat(result.pageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns buildings sorted alphabetically ascending by name, regardless of the order they "
            + "were saved in")
    void findAll_sortAsc_returnsItemsInAscendingOrder() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(null, null, 0, 10, "name", "asc");

        assertThat(result.content())
                .extracting(PublicBuilding::getName)
                .containsExactly("Alpha", "Beta", "Gamma");
    }

    @Test
    @DisplayName("returns buildings sorted alphabetically descending by name, regardless of the order they "
            + "were saved in")
    void findAll_sortDesc_returnsItemsInDescendingOrder() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "Gamma", "Addr 3"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Alpha", "Addr 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Beta",  "Addr 2"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(null, null, 0, 10, "name", "desc");

        assertThat(result.content())
                .extracting(PublicBuilding::getName)
                .containsExactly("Gamma", "Beta", "Alpha");
    }

    // =====================================================================
    // findAll — filtering
    // =====================================================================

    @Test
    @DisplayName("matches buildings whose name contains the filter text \"city\", case-insensitively, and "
            + "excludes the ones that don't")
    void findAll_nameFilter_returnsCaseInsensitiveContainsMatches() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "City Hall", "Main St 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "City Library", "Main St 2"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Fire Station", "Oak Ave 5"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll("city", null, 0, 10, "name", "asc");

        assertThat(result.content())
                .extracting(PublicBuilding::getName)
                .containsExactlyInAnyOrder("City Hall", "City Library");
    }

    @Test
    @DisplayName("matches buildings whose location contains the filter text \"MAIN\" case-insensitively, "
            + "even though the filter is typed in uppercase and the stored value isn't")
    void findAll_locationFilter_returnsCaseInsensitiveContainsMatches() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "City Hall", "Main St 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "Fire Station", "Oak Ave 5"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll(null, "MAIN", 0, 10, "name", "asc");

        assertThat(result.content())
                .extracting(PublicBuilding::getName)
                .containsExactly("City Hall");
    }

    @Test
    @DisplayName("returns only the building matching both the name and location filters together, using two "
            + "buildings that each satisfy only one filter alone, to prove the filters are AND-ed rather than OR-ed")
    void findAll_nameAndLocationFilter_combinedWithAnd() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "City Hall", "Main St 1"));
        repository.save(new PublicBuilding(UUID.randomUUID(), "City Hall Annex", "Oak Ave 5"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll("City Hall", "Main", 0, 10, "name", "asc");

        assertThat(result.content())
                .extracting(PublicBuilding::getName)
                .containsExactly("City Hall");
    }

    @Test
    @DisplayName("returns an empty page, not an error, when the name filter matches nothing")
    void findAll_noFilterMatch_returnsEmptyContent() {
        repository.save(new PublicBuilding(UUID.randomUUID(), "City Hall", "Main St 1"));
        em.flush();
        em.clear();

        PagedResult<PublicBuilding> result = repository.findAll("Nonexistent", null, 0, 10, "name", "asc");

        assertThat(result.content()).isEmpty();
    }

    // =====================================================================
    // consumption persistence
    // =====================================================================

    @Test
    @DisplayName("persists a building's consumption change (50 kW) across a save/reload round trip")
    void save_withChangedConsumption_persistsConsumption() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building, DEVICE_ID, DeviceType.BATTERY, CAPACITY_100_KW);
        building.changeConsumption(new Energy(new BigDecimal("50"), EnergyUnit.kW));

        repository.save(building);
        em.flush();
        em.clear();

        PublicBuilding found = repository.findById(BUILDING_ID).orElseThrow();

        assertThat(found.getConsumption().value()).isEqualByComparingTo("50");
        assertThat(found.getConsumption().unit()).isEqualTo(EnergyUnit.kW);
    }

    // =====================================================================
    // findEligibleForSubsidy — projection + filtering
    // =====================================================================

    @Test
    @DisplayName("includes a building with 2 devices and consumption above 50 kW in the subsidy-eligible "
            + "results, with every summary field populated correctly")
    void findEligibleForSubsidy_twoDevicesAndConsumptionAbove50_isIncludedWithCorrectFields() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
        addProducingDevice(building, UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW);
        building.changeConsumption(new Energy(new BigDecimal("75"), EnergyUnit.kW));
        repository.save(building);
        em.flush();
        em.clear();

        PagedResult<PublicBuildingSummary> result = repository.findEligibleForSubsidy(0, 10, "name", "asc");

        assertThat(result.content()).hasSize(1);
        PublicBuildingSummary summary = result.content().get(0);
        assertThat(summary.id()).isEqualTo(BUILDING_ID);
        assertThat(summary.name()).isEqualTo("City Hall");
        assertThat(summary.location()).isEqualTo("Main St 1");
        assertThat(summary.consumptionValue()).isEqualByComparingTo("75");
        assertThat(summary.consumptionUnit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    @DisplayName("excludes a building with fewer than 2 devices from the subsidy-eligible results, against a "
            + "real database query rather than the in-memory Specification")
    void findEligibleForSubsidy_fewerThanTwoDevices_isExcluded() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building, DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW);
        building.changeConsumption(new Energy(new BigDecimal("100"), EnergyUnit.kW));
        repository.save(building);
        em.flush();
        em.clear();

        PagedResult<PublicBuildingSummary> result = repository.findEligibleForSubsidy(0, 10, "name", "asc");

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("excludes a building whose consumption is exactly 50 kW from the subsidy-eligible results, "
            + "proving the strictly-greater-than-50 boundary holds in the real SQL query too, not just in the "
            + "in-memory domain check")
    void findEligibleForSubsidy_consumptionAtExactly50_isExcluded() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
        addProducingDevice(building, UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW);
        building.changeConsumption(new Energy(new BigDecimal("50"), EnergyUnit.kW));
        repository.save(building);
        em.flush();
        em.clear();

        PagedResult<PublicBuildingSummary> result = repository.findEligibleForSubsidy(0, 10, "name", "asc");

        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("returns only the eligible building when an eligible and an ineligible building are saved "
            + "side by side")
    void findEligibleForSubsidy_mixedBuildings_returnsOnlyEligibleOnes() {
        PublicBuilding eligible = new PublicBuilding(UUID.randomUUID(), "Eligible Hall", "Addr 1");
        addProducingDevice(eligible, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
        addProducingDevice(eligible, UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW);
        eligible.changeConsumption(new Energy(new BigDecimal("60"), EnergyUnit.kW));

        PublicBuilding ineligible = new PublicBuilding(UUID.randomUUID(), "Ineligible Hall", "Addr 2");
        addProducingDevice(ineligible, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
        ineligible.changeConsumption(new Energy(new BigDecimal("100"), EnergyUnit.kW));

        repository.save(eligible);
        repository.save(ineligible);
        em.flush();
        em.clear();

        PagedResult<PublicBuildingSummary> result = repository.findEligibleForSubsidy(0, 10, "name", "asc");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Eligible Hall");
    }

    @Test
    @DisplayName("returns the correct total-count and page-count metadata for the subsidy-eligible query when "
            + "results span multiple pages")
    void findEligibleForSubsidy_pagination_returnsCorrectPageMetadata() {
        for (int i = 0; i < 3; i++) {
            PublicBuilding building = new PublicBuilding(UUID.randomUUID(), "Building " + i, "Addr " + i);
            addProducingDevice(building, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
            addProducingDevice(building, UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW);
            building.changeConsumption(new Energy(new BigDecimal("75"), EnergyUnit.kW));
            repository.save(building);
        }
        em.flush();
        em.clear();

        PagedResult<PublicBuildingSummary> result = repository.findEligibleForSubsidy(0, 2, "name", "asc");

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(3L);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.pageNumber()).isEqualTo(0);
        assertThat(result.pageSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("returns subsidy-eligible buildings sorted descending by name, even when both buildings tie "
            + "on device count and consumption")
    void findEligibleForSubsidy_descSort_returnsDescendingOrder() {
        PublicBuilding a = new PublicBuilding(UUID.randomUUID(), "A Hall", "Addr A");
        addProducingDevice(a, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
        addProducingDevice(a, UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW);
        a.changeConsumption(new Energy(new BigDecimal("75"), EnergyUnit.kW));

        PublicBuilding b = new PublicBuilding(UUID.randomUUID(), "B Hall", "Addr B");
        addProducingDevice(b, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
        addProducingDevice(b, UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW);
        b.changeConsumption(new Energy(new BigDecimal("75"), EnergyUnit.kW));

        repository.save(a);
        repository.save(b);
        em.flush();
        em.clear();

        PagedResult<PublicBuildingSummary> result = repository.findEligibleForSubsidy(0, 10, "name", "desc");

        assertThat(result.content()).extracting(PublicBuildingSummary::name)
                .containsExactly("B Hall", "A Hall");
    }
}
