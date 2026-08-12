package com.example.smartcityback.asset.infrastructure.persistence.transaction;

import com.example.smartcityback.asset.application.command.AddDeviceCommand;
import com.example.smartcityback.asset.application.command.ChangeConsumptionCommand;
import com.example.smartcityback.asset.application.command.ChangeProductionCommand;
import com.example.smartcityback.asset.domain.exception.DeviceNotFoundException;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.application.service.PublicBuildingAppService;
import com.example.smartcityback.asset.domain.exception.BuildingTotalCapacityExceededException;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.infrastructure.persistence.embedded.EnergyEmbeddable;
import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import com.example.smartcityback.asset.infrastructure.persistence.interfaces.PublicBuildingJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * NOTE: These tests do NOT have @Transactional at the class level intentionally.
 * We need real commits and rollbacks (not test-managed rollbacks) to verify @Transactional
 * behaviour on the service methods.
 *
 * DB setup is done directly via JpaRepository (bypassing the domain) to keep test setup
 * fast and independent of domain construction logic.
 */


@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PublicBuildingTransactionalTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("smartcity_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PublicBuildingAppService service;

    @Autowired
    private PublicBuildingJpaRepository jpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID buildingId;

    // -----------------------------------------------------------------------
    // Setup: insert a valid row directly via JPA.
    // -----------------------------------------------------------------------

    @BeforeEach
    void insertBuildingDirectly() {
        buildingId = UUID.randomUUID();

        PublicBuildingJpaEntity entity = new PublicBuildingJpaEntity(buildingId, "City Hall", "Main St 1");
        entity.setConsumption(new EnergyEmbeddable(BigDecimal.ZERO, EnergyUnit.kW));
        entity.setDevices(new ArrayList<>());

        jpaRepository.save(entity);
    }

    @AfterEach
    void cleanup() {
        jpaRepository.deleteAll();
    }

    // =====================================================================
    // Commit: successful change is persisted to DB
    // =====================================================================

    @Test
    @DisplayName("commits a successful consumption change to the database, verified by reading the row back "
            + "outside the service's own transaction")
    void changeConsumption_success_commitsToDatabase() {
        // Building has no devices → total capacity = 0 kW.
        // Consumption of 0 kW satisfies the check (0 <= 0) and the transaction must commit.
        // version=0L matches the version Hibernate assigns on the row inserted in @BeforeEach.
        ChangeConsumptionCommand cmd = new ChangeConsumptionCommand(BigDecimal.ZERO, EnergyUnit.kW, 0L);

        assertThatCode(() -> service.changeConsumption(buildingId, cmd))
                .doesNotThrowAnyException();

        // Verify the row was actually written — re-read from DB outside the service transaction.
        PublicBuildingJpaEntity persisted = jpaRepository.findById(buildingId).orElseThrow();
        assertThat(persisted.getConsumption().getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =====================================================================
    // Rollback: domain exception prevents any DB write
    // =====================================================================

    @Test
    @DisplayName("leaves an existing building's device list completely untouched when a request to add a "
            + "device to a different, nonexistent building fails")
    void addDevice_buildingNotFound_transactionRollsBack_noOrphanedDevices() {
        // Non-existent buildingId → BuildingNotFoundException is thrown before
        // any domain mutation or save — device count must stay at 0.
        UUID unknownBuildingId = UUID.randomUUID();
        int deviceCountBefore = transactionTemplate.execute(status ->
                jpaRepository.findById(buildingId)
                        .map(b -> b.getDevices().size())
                        .orElse(0));

        AddDeviceCommand cmd = new AddDeviceCommand(
                unknownBuildingId, "Test Device", DeviceType.SOLAR, new BigDecimal("50"), EnergyUnit.kW, 0L);

        assertThatThrownBy(() -> service.addDevice(cmd))
                .isInstanceOf(BuildingNotFoundException.class);

        // The existing building must be completely untouched.
        int deviceCount = transactionTemplate.execute(status ->
                jpaRepository.findById(buildingId).orElseThrow().getDevices().size());
        assertThat(deviceCount).isEqualTo(deviceCountBefore);
    }

    @Test
    @DisplayName("leaves the database consumption value unchanged when a capacity-exceeded error is thrown "
            + "before the save is ever reached")
    void changeConsumption_domainException_transactionRollsBack_dbUnchanged() {
        // Building has no devices → total capacity = 0 kW.
        // Requesting 50 kW consumption throws BuildingTotalCapacityExceededException
        // before the repository.save() is ever reached.
        ChangeConsumptionCommand cmd = new ChangeConsumptionCommand(new BigDecimal("50"), EnergyUnit.kW, 0L);

        assertThatThrownBy(() -> service.changeConsumption(buildingId, cmd))
                .isInstanceOf(BuildingTotalCapacityExceededException.class);

        // DB row must be unchanged — consumption stays at 0.
        PublicBuildingJpaEntity persisted = jpaRepository.findById(buildingId).orElseThrow();
        assertThat(persisted.getConsumption().getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("leaves the database completely unchanged when changing production on a nonexistent device "
            + "fails before the save is ever reached")
    void changeProduction_domainException_transactionRollsBack_dbUnchanged() {
        // Building has no devices → DeviceNotFoundException is thrown inside changeDeviceProduction
        // before repository.save() is reached — DB must remain unchanged.
        UUID unknownDeviceId = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeProduction(
                buildingId,
                unknownDeviceId,
                new ChangeProductionCommand(new BigDecimal("50"), EnergyUnit.kW, 0L)))
                .isInstanceOf(DeviceNotFoundException.class);

        // DB row must be unchanged — no devices, consumption still 0.
        int deviceCount = transactionTemplate.execute(status ->
                jpaRepository.findById(buildingId).orElseThrow().getDevices().size());
        assertThat(deviceCount).isEqualTo(0);
        PublicBuildingJpaEntity persisted = jpaRepository.findById(buildingId).orElseThrow();
        assertThat(persisted.getConsumption().getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

}
