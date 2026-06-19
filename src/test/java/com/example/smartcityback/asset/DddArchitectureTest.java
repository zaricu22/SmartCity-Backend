package com.example.smartcityback.asset;

/*
 * ============================================================
 * DDD ARCHITECTURE RULES — ArchUnit enforcement
 * ============================================================
 *
 * CAN be enforced (structural, bytecode-visible):
 *  1.  Layer dependencies: Domain → none, Application → Domain,
 *      Infrastructure → Domain + Application, WebApi → Application
 *      Exceptions: webapi may access domain.shared (shared-kernel enums),
 *      webapi.exception may access domain.exception (GlobalExceptionHandler must catch domain exceptions),
 *      webapi.websocket may access domain.event + domain.valueobject (WebSocket event handler)
 *  2.  Controllers only access Application layer
 *  3.  No Spring annotations in Domain (@Service, @Component, @Repository, @Transactional)
 *  4.  No JPA annotations in Domain (@Entity, @Table, @MappedSuperclass)
 *  5.  JPA @Entity only in infrastructure.persistence.entity
 *  6.  Repository interfaces only in domain.repository (Spring Data JPA repos in infrastructure excluded)
 *  7.  Repository implementations only in infrastructure
 *  8.  Internal entities (domain.entity) not accessed from webapi
 *  9.  No public set* methods in domain classes
 * 10.  Value Object fields must be final
 * 11.  Value Objects must override equals() and hashCode()
 * 12.  Domain Services must not depend on Repository
 * 13.  Domain Specifications in domain.specification,
 *      JPA Specifications in infrastructure.persistence.specification
 * 14.  No cycles between layers
 * 15.  Composition over inheritance — domain classes do not extend other domain classes
 * 16.  Naming conventions: *Service, *Controller, *Repository,
 *      *EventHandler, *Specification in their correct packages
 * 17.  Domain exceptions must reside in domain.exception
 *      Exception: application.exception may extend NotFoundException (a domain type) because
 *      BuildingNotFoundException is an application workflow concern that uses the shared
 *      NotFoundException base for GlobalExceptionHandler's single @ExceptionHandler(NotFoundException.class)
 * 18.  Command classes (*Command) must reside in application.command
 * 19.  DTO classes (*Dto) must reside in application.dto
 * 20.  Request classes (*Request) must reside in webapi.request
 * 21.  Response classes (*Response) must reside in webapi.response
 * 22.  Domain event classes (*Event) must reside in domain.event
 * 23.  All classes in domain.event must implement DomainEvent
 * 24.  All classes in domain.exception must extend DomainException
 * 25.  Application services must not depend on other application services
 * 26.  Shared kernel (asset.shared) must not depend on any layer
 *
 * CANNOT be enforced (runtime / behavioral):
 *  1.  Constructor validates mandatory fields and invariants
 *  2.  Methods protect invariants on every state change
 *  3.  Always-valid model (canExecute / guard clauses present)
 *  4.  Domain events published after every state change
 *  5.  One aggregate per transaction
 *  6.  Getters return unmodifiable collections (ArchUnit sees List, not unmodifiableList)
 *  7.  Getters not used for business decisions (design intent, not structure)
 *  8.  Application Service contains no business logic
 *  9.  equals()/hashCode() correctness — can check overridden, not whether implementation is correct
 * 10.  Domain Service is truly stateless
 * 11.  Ubiquitous Language — names reflect domain terminology
 * 12.  Aggregate references another aggregate by ID only (intent, not detectable by type)
 * 13.  Subclass validates only what applies to it
 * ============================================================
 */

import com.example.smartcityback.asset.domain.event.DomainEvent;
import com.example.smartcityback.asset.domain.exception.DomainException;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "com.example.smartcityback.asset",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DddArchitectureTest {

    private static final String DOMAIN         = "com.example.smartcityback.asset.domain..";
    private static final String APPLICATION    = "com.example.smartcityback.asset.application..";
    private static final String INFRASTRUCTURE = "com.example.smartcityback.asset.infrastructure..";
    private static final String WEBAPI         = "com.example.smartcityback.asset.webapi..";
    private static final String SHARED         = "com.example.smartcityback.asset.shared..";

    // ----------------------------------------------------------------
    // 1. LAYER DEPENDENCIES
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule layerDependencies = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .layer("WebApi").definedBy(WEBAPI)
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application")
            .whereLayer("WebApi").mayOnlyAccessLayers("Application")
            // domain.shared.enums are shared-kernel types (DeviceType, EnergyUnit, ErrorCode)
            .ignoreDependency(
                    JavaClass.Predicates.resideInAPackage("..webapi.."),
                    JavaClass.Predicates.resideInAPackage("..domain.shared.."))
            // GlobalExceptionHandler must reference domain exception types to catch them
            .ignoreDependency(
                    JavaClass.Predicates.resideInAPackage("..webapi.exception.."),
                    JavaClass.Predicates.resideInAPackage("..domain.exception.."))
            // BuildingWebSocketEventHandler bridges domain events to WebSocket clients
            .ignoreDependency(
                    JavaClass.Predicates.resideInAPackage("..webapi.websocket.."),
                    JavaClass.Predicates.resideInAPackage("..domain.event.."))
            .ignoreDependency(
                    JavaClass.Predicates.resideInAPackage("..webapi.websocket.."),
                    JavaClass.Predicates.resideInAPackage("..domain.valueobject.."));

    // ----------------------------------------------------------------
    // 2. DOMAIN ISOLATION — no Spring or JPA annotations
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule noSpringAnnotationsInDomain = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
            .orShould().beAnnotatedWith(org.springframework.stereotype.Component.class)
            .orShould().beAnnotatedWith(org.springframework.stereotype.Repository.class);

    @ArchTest
    static final ArchRule noTransactionalInDomain = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);

    @ArchTest
    static final ArchRule domainEventsMustImplementDomainEvent = classes()
            .that().resideInAPackage("..domain.event..")
            .and().areNotInterfaces()
            .should().implement(DomainEvent.class);

    @ArchTest
    static final ArchRule noJpaAnnotationsInDomain = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().beAnnotatedWith(jakarta.persistence.Entity.class)
            .orShould().beAnnotatedWith(jakarta.persistence.Table.class)
            .orShould().beAnnotatedWith(jakarta.persistence.MappedSuperclass.class);

    // ----------------------------------------------------------------
    // 3. JPA ENTITIES STAY IN INFRASTRUCTURE
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule jpaEntitiesOnlyInInfrastructure = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage(INFRASTRUCTURE);

    // ----------------------------------------------------------------
    // 4. AGGREGATE BOUNDARY — internal entities hidden from webapi
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule internalEntitiesHiddenFromWebApi = noClasses()
            .that().resideInAPackage(WEBAPI)
            .should().dependOnClassesThat().resideInAPackage("..domain.entity..");

    // ----------------------------------------------------------------
    // 5. REPOSITORY PLACEMENT
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule repositoryInterfacesInDomain = classes()
            .that().areInterfaces()
            .and().haveSimpleNameEndingWith("Repository")
            .and().resideOutsideOfPackage(INFRASTRUCTURE)
            .should().resideInAPackage("..domain.repository..");

    @ArchTest
    static final ArchRule repositoryImplementationsInInfrastructure = classes()
            .that().areNotInterfaces()
            .and().implement(JavaClass.Predicates.resideInAPackage("..domain.repository.."))
            .should().resideInAPackage(INFRASTRUCTURE);

    // ----------------------------------------------------------------
    // 6. DOMAIN SERVICE — must not depend on Repository
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule domainServicesMustNotDependOnRepository = noClasses()
            .that().resideInAPackage("..domain.service..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);

    // ----------------------------------------------------------------
    // 7. NO SETTERS IN DOMAIN
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule noPublicSettersInDomain = methods()
            .that().haveNameMatching("set[A-Z].*")
            .and().areDeclaredInClassesThat().resideInAPackage(DOMAIN)
            .should().notBePublic()
            .allowEmptyShould(true);

    // ----------------------------------------------------------------
    // 9. VALUE OBJECTS — final fields, equals/hashCode overridden
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule valueObjectFieldsMustBeFinal = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain.valueobject..")
            .and().areNotStatic()
            .should().beFinal();

    @ArchTest
    static final ArchRule valueObjectsMustOverrideEqualsAndHashCode = classes()
            .that().resideInAPackage("..domain.valueobject..")
            .should(overrideMethod("equals"))
            .andShould(overrideMethod("hashCode"));

    // ----------------------------------------------------------------
    // 10. SPECIFICATION PLACEMENT
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule domainSpecificationsInDomainLayer = classes()
            .that().haveSimpleNameEndingWith("Specification")
            .and().resideOutsideOfPackage(INFRASTRUCTURE)
            .should().resideInAPackage("..domain.specification..");

    @ArchTest
    static final ArchRule jpaSpecificationsInInfrastructure = classes()
            .that().haveSimpleNameEndingWith("JpaSpecification")
            .should().resideInAPackage(INFRASTRUCTURE);

    // ----------------------------------------------------------------
    // 11. COMPOSITION OVER INHERITANCE
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule compositionOverInheritance = classes()
            .that().resideInAnyPackage("..domain.aggregate..", "..domain.entity..", "..domain.valueobject..")
            .should(notExtendAnotherDomainClass());

    // ----------------------------------------------------------------
    // 12. NAMING CONVENTIONS
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule serviceNaming = classes()
            .that().resideInAPackage("..application.service..")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule controllerNaming = classes()
            .that().resideInAPackage("..webapi.controller..")
            .should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule repositoryNaming = classes()
            .that().resideInAPackage("..domain.repository..")
            .should().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule eventHandlerNaming = classes()
            .that().resideInAPackage("..application.eventhandler..")
            .should().haveSimpleNameEndingWith("EventHandler");

    @ArchTest
    static final ArchRule specificationNaming = classes()
            .that().resideInAPackage("..domain.specification..")
            .should().haveSimpleNameEndingWith("Specification");

    @ArchTest
    static final ArchRule commandPlacement = classes()
            .that().haveSimpleNameEndingWith("Command")
            .should().resideInAPackage("..application.command..");

    @ArchTest
    static final ArchRule dtoPlacement = classes()
            .that().haveSimpleNameEndingWith("Dto")
            .should().resideInAPackage("..application.dto..");

    @ArchTest
    static final ArchRule requestPlacement = classes()
            .that().haveSimpleNameEndingWith("Request")
            .should().resideInAPackage("..webapi.request..");

    @ArchTest
    static final ArchRule responsePlacement = classes()
            .that().haveSimpleNameEndingWith("Response")
            .should().resideInAPackage("..webapi.response..");

    @ArchTest
    static final ArchRule eventPlacement = classes()
            .that().haveSimpleNameEndingWith("Event")
            .should().resideInAPackage("..domain.event..");

    // ----------------------------------------------------------------
    // 13. DOMAIN EXCEPTIONS
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule domainExceptionsLocation = classes()
            .that().areAssignableTo(DomainException.class)
            .and().resideOutsideOfPackage("..application.exception..")
            .should().resideInAPackage("..domain.exception..");

    @ArchTest
    static final ArchRule domainExceptionsMustExtendDomainException = classes()
            .that().resideInAPackage("..domain.exception..")
            .should().beAssignableTo(DomainException.class);

    // ----------------------------------------------------------------
    // 14. APPLICATION SERVICE ISOLATION
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule appServicesMustNotDependOnEachOther = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAPackage("..application.service..");

    // ----------------------------------------------------------------
    // 15. NO CYCLES BETWEEN LAYERS
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule noCyclesBetweenLayers = slices()
            .matching("com.example.smartcityback.asset.(*)..")
            .should().beFreeOfCycles();

    // ----------------------------------------------------------------
    // 16. SHARED KERNEL ISOLATION
    // ----------------------------------------------------------------

    @ArchTest
    static final ArchRule sharedMustNotDependOnAnyLayer = noClasses()
            .that().resideInAPackage(SHARED)
            .should().dependOnClassesThat()
            .resideInAnyPackage(DOMAIN, APPLICATION, INFRASTRUCTURE, WEBAPI);

    // ----------------------------------------------------------------
    // Custom conditions
    // ----------------------------------------------------------------

    private static ArchCondition<JavaClass> notExtendAnotherDomainClass() {
        return new ArchCondition<JavaClass>("not extend another domain class") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                clazz.getRawSuperclass().ifPresent(superClass -> {
                    String pkg = superClass.getPackageName();
                    boolean superIsInDomain = pkg.contains(".domain.aggregate")
                            || pkg.contains(".domain.entity")
                            || pkg.contains(".domain.valueobject");
                    if (superIsInDomain) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getSimpleName() + " extends domain class "
                                        + superClass.getSimpleName()));
                    }
                });
            }
        };
    }

    private static ArchCondition<JavaClass> overrideMethod(String methodName) {
        return new ArchCondition<JavaClass>("override " + methodName + "()") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                boolean overrides = clazz.getMethods().stream()
                        .anyMatch(m -> m.getName().equals(methodName)
                                && m.getOwner().getName().equals(clazz.getName()));
                if (!overrides) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getSimpleName() + " must override " + methodName + "()"));
                }
            }
        };
    }
}
