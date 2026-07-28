# Spring vs ASP.NET — What This Project Demonstrates

Grounded analysis of the comparison list against actual code in this repo.
Each item is marked as **demonstrated** (feature is actively used), **partial**, or **not applicable** (feature not used in this project).

---

## Where Spring has the edge — features this project relies on

### `@Transactional` — demonstrated

`PublicBuildingAppService` is annotated `@Transactional` at class level.
`PublicBuildingQueryService` uses `@Transactional(readOnly=true)` — this CQRS split is one annotation.

In ASP.NET: no equivalent annotation. You would wrap service methods in `using var tx = await _db.BeginTransactionAsync()` manually, or implement a `UnitOfWork` pattern with explicit commit/rollback. The read-only optimization hint (which tells Hibernate to skip dirty checking and flush) has no direct equivalent in EF Core.

### `@EventListener` internal event bus — demonstrated

`AuditLogEventHandler` and `BuildingWebSocketEventHandler` each have three `@EventListener` methods for `DeviceAddedEvent`, `ConsumptionChangedEvent`, `ProductionChangedEvent`. `AppService` just calls `eventPublisher.publishEvent(event)`. Both handlers are discovered automatically — no registration code anywhere.

In ASP.NET: requires MediatR or a similar third-party library. Each handler must implement `INotificationHandler<T>` and be registered with `services.AddMediatR(...)`. Equivalent functionality, but two extra dependencies and explicit wiring.

### `@Scheduled` — demonstrated

`TokenBlacklist.evictExpiredTokens()` is annotated `@Scheduled(fixedRate = 3_600_000)` — one annotation schedules an hourly background job.

In ASP.NET: requires implementing `IHostedService` or `BackgroundService`, overriding `ExecuteAsync`, and setting up a `PeriodicTimer`. Roughly 20 lines of boilerplate for what Spring does in one annotation.

### `@PreAuthorize` method-level security — demonstrated

`PublicBuildingController` has `@PreAuthorize("hasRole('ADMIN')")` on every mutation endpoint and `@PreAuthorize("hasAnyRole('VIEWER', 'ADMIN')")` on reads — all enforced by Spring AOP before the method body runs.

In ASP.NET: `[Authorize(Roles = "Admin")]` works at controller/action level, but SpEL expressions (`hasRole`, `hasAnyRole`, `#param == authentication.name`) are not supported. Complex authorization rules require Policy-based authorization: define a policy in `AddAuthorization(options => options.AddPolicy(...))`, then `[Authorize(Policy = "RequireAdmin")]` — significantly more verbose for fine-grained control.

### Profile-based configuration — demonstrated

Four active profiles: `dev`, `ci`, `prod` (application-\*.properties), and `test` (applied by Testcontainers tests).
`logback-spring.xml` uses `<springProfile name="dev">` and `<springProfile name="prod">` blocks to switch log format and appender per environment.

In ASP.NET: `appsettings.Development.json` / `appsettings.Production.json` cover configuration override. But there is no equivalent of `logback-spring.xml` with per-profile logging — you would need separate NLog/Serilog configurations loaded by environment name. Profile-switched logging in a single file is a Spring-specific convenience.

### Spring Security OAuth2 — demonstrated

`OAuth2SuccessHandler` extends `SimpleUrlAuthenticationSuccessHandler` and handles the full Google login callback: extracts email from `OAuth2User`, calls `InMemoryUserRegistry.findOrRegisterOAuth()`, issues JWT + refresh token, redirects with tokens in the URL fragment. Spring handles the authorization code exchange, token endpoint call, and userinfo fetch automatically.

In ASP.NET: built-in OAuth2 support exists but the customization API differs. `OnTicketReceived` / `OnCreatingTicket` callbacks inside `AddGoogle(options => ...)` play the same role, but the integration with a custom JWT issuance pipeline requires more wiring.

### Test slices — demonstrated

- `@WebMvcTest(PublicBuildingController.class)` — loads only the web layer, controllers, and exception handlers. JPA context not started.
- `@DataJpaTest` — loads only JPA context and repository. No web layer, no services.
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — full integration test with real HTTP via REST Assured.
- `@SpringBootTest(webEnvironment = NONE)` — full context but no servlet, used by WireMock test.

In ASP.NET: `WebApplicationFactory<T>` is the integration test approach — it loads the full application pipeline even for what would be controller-layer-only tests. There is no direct `@DataJpaTest` equivalent; EF Core tests typically use an in-memory SQLite context manually configured per test class.

### `@MockitoBean` in slice tests — demonstrated

`PublicBuildingControllerAPITest` and `AuthControllerTest` use `@MockitoBean` (Spring Boot 3.4+ replacement for `@MockBean`) to replace service beans in the Spring context with Mockito mocks, without loading the full application context.

In ASP.NET: `WebApplicationFactory` supports `builder.ConfigureTestServices(services => services.AddSingleton<IService>(mockService))` to swap implementations — same concept but more verbose setup per test class.

### AOP via `@Transactional` and `@EventListener` — demonstrated

Both annotations are implemented by Spring AOP — CGLIB proxies wrap the annotated beans. No explicit proxy configuration; the annotations are the entire setup.

In ASP.NET: middleware and action filters handle HTTP cross-cutting concerns, but there is no equivalent interception mechanism for arbitrary service methods. Decorating a service method with a custom attribute (e.g. `[Transactional]`) does not do anything — you would need a framework like Castle DynamicProxy or Scrutor to intercept method calls.

### Springdoc OpenAPI — partial (third-party, but deeply integrated)

`springdoc-openapi-starter-webmvc-ui` generates the spec from annotations (`@Tag`, `@Operation`, `@ApiResponse`) and the `enrich_openapi.py` CI step enriches it with Claude. The spec is served at `/v3/api-docs` and loaded by Swagger UI.

In ASP.NET: built-in OpenAPI support (`Microsoft.AspNetCore.OpenApi`) added in .NET 9 is cleaner than Springdoc and does not require a third-party dependency. Springdoc has had breaking changes across Spring Boot versions (Springfox was abandoned entirely) — this is a real maintenance risk.

---

## Where ASP.NET has the edge — pain points visible in this project's code

### Rate limiting — third-party dependency

This project uses `bucket4j-core` (third-party) for `RateLimitFilter`. `RateLimitFilter` is 90+ lines including manual bucket management, IP extraction, and path matching.

In ASP.NET: built-in rate limiting middleware since .NET 7. `builder.Services.AddRateLimiter(options => options.AddFixedWindowLimiter(...))` + `app.UseRateLimiter()`. No third-party library needed.

### RFC 7807 Problem Details — not implemented

This project has a custom `ErrorResponse` record (`errorCode`, `message`, `status`, `timestamp`, `requestId`) instead of the HTTP standard `application/problem+json`.

In ASP.NET: `ProblemDetails` support is built in. `builder.Services.AddProblemDetails()` and returning `TypedResults.Problem(...)` from exception handlers gives fully compliant RFC 7807 responses out of the box.

### Criteria API verbosity — visible in `SubsidyEligibilityJpaSpecification`

`SubsidyEligibilityJpaSpecification` is ~40 lines of Criteria API to express three predicates (consumption > 50, location LIKE 'Zone A%', COUNT subquery ≥ 2). The Criteria API requires a `CriteriaBuilder`, `Root<T>`, and explicit `Predicate` construction for every condition.

In ASP.NET with EF Core + LINQ:
```csharp
buildings.Where(b =>
    b.ConsumptionValue > 50 &&
    b.Location.StartsWith("Zone A") &&
    b.Devices.Count >= 2)
```
Three lines, no imports, fully type-safe. LINQ is a genuine readability advantage for complex query composition.

### Startup time and memory footprint

Spring Boot with auto-configuration, Hibernate, Spring Security, and WebSocket support starts noticeably slowly. On Render's free tier (which cold-starts instances after inactivity), this translates to visible first-request latency. ASP.NET consistently starts faster and uses less memory under equivalent load — relevant when container resource limits are tight.

### Springdoc compatibility risk

Springdoc is a third-party dependency. `springdoc-openapi-starter-webmvc-ui` has had breaking API changes across Spring Boot versions and its predecessor (Springfox) was abandoned entirely with no migration path. The AI enrichment step (`enrich_openapi.py`) depends on the schema shape being stable.

In ASP.NET: built-in OpenAPI support is part of the framework, versioned with the runtime.

### Lombok — minimal but present

This project uses Lombok only for `@Slf4j` (6 classes: `AppService`, `RepositoryImpl`, `GlobalExceptionHandler`, `Controller`, `AuditLogEventHandler`, `WebSocketEventHandler`). The full `@Data` / `@Builder` abuse common in Spring projects is avoided — 29 records handle all DTOs and commands without Lombok.

However, `@Slf4j` still requires the Lombok annotation processor in the build. In ASP.NET: `ILogger<T>` is injected via constructor like any other dependency — no annotation processor, no IDE plugin, no build-step dependency.

### `@WebMvcTest` auto-picks unrelated filter beans — visible in `PublicBuildingControllerAPITest.java:54-77`

The controller slice test excludes three security auto-configurations:
```java
excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ClientAutoConfiguration.class
}
```
And still needs two extra `@MockitoBean` declarations because `JwtAuthFilter` and `RateLimitFilter` are `@Component` beans that `@WebMvcTest` picks up automatically regardless. The test comment explains: *"JwtAuthFilter and RateLimitFilter are @Component Filter beans — @WebMvcTest picks them up. Mock their dependencies so the context loads; neither is ever invoked."*

A controller test written to verify exception-to-HTTP mapping ended up needing to know about `JwtTokenService` and `TokenBlacklist` — two classes from the `auth` context with no relation to the controller under test. Spring's component scanning makes this non-obvious when it breaks.

In ASP.NET: `WebApplicationFactory.ConfigureTestServices()` is explicit — you register exactly what you want, nothing is auto-discovered.

### Circular dependency workaround in `SecurityConfig` — visible at `SecurityConfig.java:68-74`

```java
// OAuth2SuccessHandler is a method parameter, not a constructor field, to break the
// potential circular dependency: SecurityConfig → OAuth2SuccessHandler →
// InMemoryUserRegistry → PasswordEncoder (a @Bean defined in SecurityConfig).
```

A 4-hop circular dependency in bean wiring forced a non-obvious workaround: inject `OAuth2SuccessHandler` as a `@Bean` method parameter instead of a constructor field. The comment exists because without it a future developer would not understand why this one injection differs from the constructor injection used everywhere else.

In ASP.NET: the DI container throws `InvalidOperationException: A circular dependency was detected` with the full cycle listed — clear, immediate, no workaround hunt required.

### OAuth2 `state` nonce forces session on a stateless JWT API — visible at `SecurityConfig.java:85-88`

```java
// IF_REQUIRED instead of STATELESS: Spring's OAuth2 client needs a brief HTTP session
// to store the 'state' nonce during the 3-leg handshake.
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
```

A stateless Bearer-token API had to use `IF_REQUIRED` (sessions sometimes created) instead of `STATELESS` (never) because Spring's OAuth2 client stores the CSRF `state` nonce in the HTTP session internally. The framework's implementation detail bleeds into your security policy.

In ASP.NET: OAuth2 correlation state is stored in an encrypted cookie (`__Correlation.`), not a session — `AddGoogle(...)` works without forcing server-side sessions.

### CSRF disabled via workaround to avoid CodeQL false positive — visible at `SecurityConfig.java:76-83`

```java
// requireCsrfProtectionMatcher(∅) is semantically identical to csrf.disable() but avoids
// triggering CodeQL java/spring-disabled-csrf-protection
// (which only checks for .disable() calls).
.csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> false))
```

The correct way to disable CSRF on a stateless JWT API (`.csrf().disable()`) triggers a CodeQL rule that pattern-matches the literal method name. The code uses a semantically identical but syntactically different workaround — fighting the framework's defaults in a non-obvious way that requires a comment to explain.

In ASP.NET: CSRF protection is off by default for APIs that don't use cookie-based auth. No workaround, no scanner conflict.

### WebSocket: STOMP configuration stack vs SignalR — visible in `WebSocketConfig.java`

Setting up real-time push required: `@EnableWebSocketMessageBroker`, implementing `WebSocketMessageBrokerConfigurer`, overriding `configureMessageBroker()` (broker prefix, application prefix) and `registerStompEndpoints()` (endpoint, SockJS fallback), plus `SimpMessagingTemplate` injected into every handler that pushes. The code's own comment flags the next limitation: *"in-memory broker for topics (replace with RabbitMQ/ActiveMQ for production scale)"* — the current setup does not survive horizontal scaling.

In ASP.NET with SignalR: `builder.Services.AddSignalR()` + `app.MapHub<BuildingHub>("/ws")`. Scaling to multiple instances: `.AddStackExchangeRedis(...)` — one line, no broker to configure separately.

---

## Not applicable to this project

Items from the comparison that don't apply because the feature is not used:

| Spring advantage | Why not applicable here |
|-----------------|------------------------|
| Flyway / Liquibase integration | No DB migrations in this project — schema managed by JPA `ddl-auto` or manual SQL |
| `@ConditionalOnProperty`, `@ConditionalOnMissingBean` | No conditional bean registration used |
| `@PostConstruct` / `@PreDestroy` / `InitializingBean` | No explicit bean lifecycle hooks used |
| Multiple datasource support | Single datasource |
| Spring Data query derivation (`findByEmailAndActiveTrue()`) | Project uses a custom `PublicBuildingRepository` interface with manual implementation — no `JpaRepository` auto-derived queries |
| Spring Batch / Spring Integration / Spring AMQP | No batch, messaging, or integration patterns used |
| Spring Retry (`@Retryable`) / Spring Cloud Circuit Breaker | No external HTTP calls in production code (WireMock test is a placeholder) |

| ASP.NET advantage | Why not applicable here |
|------------------|------------------------|
| LINQ vs JPQL | Project does not write JPQL queries — only Criteria API (for Specification) and Spring Data conventions |
| `WebApplicationFactory` vs `@SpringBootTest` | Both styles are present; this is actually a draw in this project |
| Lombok `@Data` / `@Builder` boilerplate | Not used — project deliberately uses Java records instead |
| Hot reload | Development ergonomics difference, not visible in production code |
| Nullable reference types | Java 17 project uses `Optional<T>` + constructor guards consistently — gap exists but is not causing bugs here |
