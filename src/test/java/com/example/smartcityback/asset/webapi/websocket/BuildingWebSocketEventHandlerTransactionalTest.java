package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.application.command.AddDeviceCommand;
import com.example.smartcityback.asset.application.service.PublicBuildingAppService;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves BuildingWebSocketEventHandler's @TransactionalEventListener(AFTER_COMMIT) actually
 * defers the WebSocket push until the surrounding transaction commits.
 *
 * BuildingWebSocketEventHandlerTest cannot verify this — it calls handler methods directly,
 * bypassing Spring's event dispatch entirely, so it would pass identically regardless of
 * whether the handler used @EventListener, AFTER_COMMIT, or AFTER_ROLLBACK. Proving the actual
 * dispatch timing requires a real PlatformTransactionManager and a real ApplicationEventPublisher
 * publishing from inside a real transaction — hence a full Spring context with Testcontainers
 * Postgres here, same infrastructure as PublicBuildingTransactionalTest, rather than mocks.
 *
 * The rollback case puts @Transactional on the test method itself: Spring's SpringExtension
 * starts that transaction via a BeforeTestExecutionCallback — after @BeforeEach (so the seed
 * row is inserted and committed independently) but before the test body runs — and rolls it
 * back by default via AfterTestExecutionCallback once the method returns (before @AfterEach).
 * PublicBuildingAppService's own @Transactional (propagation REQUIRED) joins that already-open
 * transaction rather than starting a new one, so the event publish never actually commits and
 * AFTER_COMMIT never fires. The commit case has no such wrapping, so AppService's own
 * transaction commits normally, and AFTER_COMMIT fires synchronously as part of that commit.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class BuildingWebSocketEventHandlerTransactionalTest {

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

    // Real bean replaced with a mock so we can assert whether/when the push happened,
    // without standing up a real STOMP broker or WebSocket session.
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private UUID buildingId;

    // -----------------------------------------------------------------------
    // Setup: insert a valid row directly via JPA, same as PublicBuildingTransactionalTest.
    // Runs in its own, already-committed transaction before each test's TestTransaction opens.
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
    // Rollback: AFTER_COMMIT must never fire
    // =====================================================================

    @Test
    @Transactional
    @DisplayName("never pushes a WebSocket update for a device addition whose surrounding transaction gets "
            + "rolled back, since AFTER_COMMIT only fires once a commit actually happens")
    void deviceAdded_transactionRollsBack_neverPushesOverWebSocket() {
        // version=0L matches the version Hibernate assigns on the row inserted in @BeforeEach.
        service.addDevice(new AddDeviceCommand(
                buildingId, "Solar Panel", DeviceType.SOLAR,
                new BigDecimal("100"), EnergyUnit.kW, 0L));

        // This @Test method is itself @Transactional, so the call above joined that still-open
        // transaction instead of committing its own. A plain @EventListener would already have
        // fired here; AFTER_COMMIT must not have, since nothing has committed yet. The method
        // returns without flagging a commit, so Spring rolls the transaction back afterward —
        // AFTER_COMMIT never fires at all for this change.
        verifyNoInteractions(messagingTemplate);
    }

    // =====================================================================
    // Commit: AFTER_COMMIT fires, and only after the commit actually happens
    // =====================================================================

    @Test
    @DisplayName("pushes a WebSocket update only once the device-addition transaction actually commits, "
            + "proving the push isn't sent optimistically before the data is durable")
    void deviceAdded_transactionCommits_pushesOverWebSocketAfterCommit() {
        // No wrapping test transaction here — PublicBuildingAppService's own @Transactional
        // starts and commits entirely within this call, so by the time addDevice() returns,
        // AFTER_COMMIT has already fired synchronously as part of that commit.
        service.addDevice(new AddDeviceCommand(
                buildingId, "Solar Panel", DeviceType.SOLAR,
                new BigDecimal("100"), EnergyUnit.kW, 0L));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings/" + buildingId + "/devices"),
                any(Object.class)
        );
    }
}
