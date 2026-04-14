# Startup Service — Interview Preparation Guide

---

## What does Startup Service do?

The Startup Service is the **core domain service** of FounderLink. It manages the lifecycle of startup listings — from creation to approval/rejection. It also handles following a startup (investors follow startups they're interested in) and searching startups by filters.

---

## Key Features

1. **Create Startup** — Founder creates a startup listing (goes to pending approval)
2. **Get/Update/Delete** — CRUD operations on startup data
3. **Admin Approval/Rejection** — Admin approves or rejects a startup listing
4. **Search with Filters** — Filter by industry, stage, funding range, location
5. **Follow Startup** — Investors/co-founders can follow a startup
6. **Redis Caching** — Startup data cached for performance
7. **RabbitMQ Events** — Publishes `startup.created` and `startup.rejected` events
8. **CQRS Pattern** — Separate command service (writes) and query service (reads)

---

## Architecture Pattern: CQRS (Command Query Responsibility Segregation)

```
StartupController
    ├── StartupCommandService  (create, update, delete, approve, reject, follow)
    └── StartupQueryService    (get, getAll, search, isFollowing)
         ↓ implemented by
    StartupServiceImpl (implements both interfaces)
```

**Why CQRS?**
- Separating reads from writes makes the code easier to understand and test.
- In theory, you could have different scaling strategies for reads (scale up query replicas) and writes (scale up command service).
- Each interface describes its contract clearly — you know at a glance which operations are read-only.

---

## File 1: SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/startups", "/startups/{id}", "/startups/search", "/startups/founder/{founderId}"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**`@EnableMethodSecurity`**
- This is CRITICAL. It enables method-level security annotations like `@PreAuthorize`.
- Without this annotation, `@PreAuthorize("hasAuthority('ROLE_FOUNDER')")` on controller methods would be completely IGNORED — everyone could call any endpoint regardless of role.
- Activates Spring's AOP proxy for security checks at the method level.

**`addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`**
- Inserts our custom `JwtAuthenticationFilter` BEFORE Spring Security's default username/password filter in the filter chain.
- This ensures the JWT is validated and the user is authenticated before any security checks happen.

**Public GET endpoints:**
- `GET /startups` — anyone (not logged in) can browse approved startups
- `GET /startups/{id}` — anyone can view a startup detail
- `GET /startups/search` — anyone can search startups
- These are public because FounderLink wants to be discoverable by potential investors browsing the web.

---

## File 2: StartupController.java

```java
@RestController
@RequestMapping("/startups")
public class StartupController {

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_FOUNDER')")
    public ResponseEntity<StartupResponse> createStartup(
            Authentication authentication,
            @Valid @RequestBody StartupRequest request) {

        Long founderId = Long.parseLong(authentication.getName());
        StartupResponse response = startupCommandService.createStartup(founderId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
```

**`@PreAuthorize("hasAuthority('ROLE_FOUNDER')")`**
- Only users with `ROLE_FOUNDER` can create startups.
- `hasAuthority('ROLE_FOUNDER')` checks if the authenticated user's authorities list contains `ROLE_FOUNDER`.
- This is evaluated BEFORE the method body executes.

```java
    @GetMapping
    public ResponseEntity<Page<StartupResponse>> getAllApprovedStartups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<StartupResponse> startups = startupQueryService.getAllApprovedStartups(pageable);
        return ResponseEntity.ok(startups);
    }
```

**No `@PreAuthorize`** — This endpoint is public (anyone can browse). The query uses `findByIsApprovedTrue(pageable)` so only approved startups appear.

```java
    @GetMapping("/search")
    public ResponseEntity<Page<StartupResponse>> searchStartups(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) StartupStage stage,
            @RequestParam(required = false) BigDecimal minFunding,
            @RequestParam(required = false) BigDecimal maxFunding,
            @RequestParam(required = false) String location,
            ...) {
```

**`required = false`** — These query parameters are optional. If not provided, their value is `null` and the search ignores that filter.

```java
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<StartupResponse> approveStartup(@PathVariable Long id) {
```

Only admins can approve startups. When approved, the startup becomes visible in the public listing.

---

## File 3: StartupServiceImpl.java — The Core Business Logic

### createStartup:

```java
@Override
@Caching(evict = {
    @CacheEvict(value = "startups", allEntries = true),
    @CacheEvict(value = "startupsByFounder", key = "#founderId")
})
public StartupResponse createStartup(Long founderId, StartupRequest request) {
    Startup startup = Startup.builder()
            .name(request.getName())
            ...
            .founderId(founderId)
            .isApproved(false)  // New startups start as unapproved
            .build();

    Startup saved = startupRepository.save(startup);

    // Publish RabbitMQ event
    StartupCreatedEvent event = StartupCreatedEvent.builder()
            .startupId(saved.getId())
            .founderId(saved.getFounderId())
            .startupName(saved.getName())
            .build();
    rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_NAME,
            RabbitMQConfig.STARTUP_CREATED_ROUTING_KEY,
            event
    );

    return startupMapper.toResponse(saved);
}
```

**`isApproved(false)`** — New startups are NOT publicly visible until an admin approves them.

**`rabbitTemplate.convertAndSend(exchange, routingKey, event)`**
- Sends `StartupCreatedEvent` to RabbitMQ.
- Exchange: `founderlink.exchange` (TopicExchange)
- Routing Key: `startup.created`
- NotificationService is listening on `startup.created.queue` bound to this routing key.
- Result: the founder receives a notification "Your startup has been submitted for review."

### getStartupById (with Caching):

```java
@Override
@Transactional(readOnly = true)
@Cacheable(value = "startups", key = "#id")
public StartupResponse getStartupById(Long id) {
    log.info("Fetching startup from DB: id={}", id);
    Startup startup = startupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Startup not found with ID: " + id));
    return startupMapper.toResponse(startup);
}
```

**`@Cacheable(value = "startups", key = "#id")`**
- Cache key: `startups::42`
- First call: hits PostgreSQL, stores result in Redis, logs "Fetching startup from DB"
- Subsequent calls: returns from Redis, no log (method body not executed)
- The log helps in testing: if you see the log repeatedly, caching isn't working.

### searchStartups (JPA Specifications):

```java
@Override
public Page<StartupResponse> searchStartups(String industry, StartupStage stage,
                                             BigDecimal minFunding, BigDecimal maxFunding,
                                             String location, Pageable pageable) {

    Specification<Startup> spec = Specification.where(StartupSpecification.isApproved());

    if (industry != null && !industry.isBlank())
        spec = spec.and(StartupSpecification.hasIndustry(industry));
    if (stage != null)
        spec = spec.and(StartupSpecification.hasStage(stage));
    if (minFunding != null)
        spec = spec.and(StartupSpecification.hasMinFunding(minFunding));
    if (maxFunding != null)
        spec = spec.and(StartupSpecification.hasMaxFunding(maxFunding));
    if (location != null && !location.isBlank())
        spec = spec.and(StartupSpecification.hasLocation(location));

    return startupRepository.findAll(spec, pageable).map(startupMapper::toResponse);
}
```

**JPA Specifications**
- A `Specification<T>` is a functional interface that encapsulates a single predicate for a JPA Criteria query.
- `Specification.where(spec1).and(spec2).and(spec3)` composes specifications: the final query is `WHERE isApproved = true AND industry = ? AND stage = ?`.
- **Why use Specifications instead of custom JPQL queries?** Because the filters are optional. With Specifications, you programmatically build the query — only adding conditions for non-null parameters. A custom JPQL query would need many overloaded methods or a complex dynamic query string.
- `startupRepository.findAll(spec, pageable)` — Spring Data JPA executes the composed specification as a SQL query.

### approveStartup (with Cache Eviction):

```java
@Override
@Caching(evict = {
    @CacheEvict(value = "startups", key = "#id"),
    @CacheEvict(value = "startupsByFounder", allEntries = true)
})
public StartupResponse approveStartup(Long id) {
    Startup startup = startupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Startup not found with ID: " + id));

    startup.setIsApproved(true);
    startup.setIsRejected(false);
    Startup approved = startupRepository.save(startup);
    return startupMapper.toResponse(approved);
}
```

When a startup is approved, the cached data for that startup is evicted from Redis. The next time someone fetches the startup, they'll get the updated data (isApproved=true) from the database and it will be re-cached.

### rejectStartup (with Event):

```java
public StartupResponse rejectStartup(Long id) {
    ...
    startup.setIsRejected(true);
    startup.setIsApproved(false);
    ...

    StartupRejectedEvent event = StartupRejectedEvent.builder()
            .startupId(rejected.getId())
            .founderId(rejected.getFounderId())
            .startupName(rejected.getName())
            .build();
    rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_NAME,
            RabbitMQConfig.STARTUP_REJECTED_ROUTING_KEY,
            event
    );
    ...
}
```

When rejected, a `startup.rejected` event is published. NotificationService listens to this and sends the founder a notification: "Your startup was not approved at this time."

---

## File 4: Startup.java (Entity)

```java
@Entity
@Table(name = "startups")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Startup {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StartupStage stage;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal fundingGoal;

    @Builder.Default
    private Boolean isApproved = false;

    @Builder.Default
    private Boolean isRejected = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

**`@Enumerated(EnumType.STRING)`**
- Stores the enum value as a string in the database (e.g., "MVP", "EARLY_TRACTION", "SEED").
- Without this, Hibernate would use `EnumType.ORDINAL` (stores the enum's position: 0, 1, 2...) — this is fragile because reordering the enum values breaks existing data.
- `EnumType.STRING` is always preferred.

**`BigDecimal fundingGoal`**
- `BigDecimal` is used for monetary values instead of `double` or `float`.
- Floating-point types like `double` can't represent decimal values exactly (e.g., 0.1 + 0.2 = 0.30000000000000004).
- `BigDecimal` is exact. `precision = 15, scale = 2` means up to 15 total digits with 2 decimal places (e.g., 9,999,999,999,999.99).

**`@Builder.Default`**
- When using Lombok's `@Builder`, fields don't get their Java default value — the builder starts with nulls.
- `@Builder.Default` tells Lombok: when building an object without specifying this field, use `false` as the default.
- Without this, `new Startup.builder().build().getIsApproved()` would return `null`.

---

## File 5: RabbitMQConfig.java

```java
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "founderlink.exchange";
    public static final String STARTUP_CREATED_QUEUE = "startup.created.queue";
    public static final String STARTUP_CREATED_ROUTING_KEY = "startup.created";
    public static final String STARTUP_REJECTED_QUEUE = "startup.rejected.queue";
    public static final String STARTUP_REJECTED_ROUTING_KEY = "startup.rejected";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue startupCreatedQueue() {
        return QueueBuilder.durable(STARTUP_CREATED_QUEUE).build();
    }

    @Bean
    public Binding startupCreatedBinding(Queue startupCreatedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(startupCreatedQueue).to(exchange).with(STARTUP_CREATED_ROUTING_KEY);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
```

**RabbitMQ Concepts:**

**Exchange** — A message router. Publishers send to an exchange, not directly to a queue.

**TopicExchange** — Routes messages based on routing key pattern matching.
- `startup.created` → goes to `startup.created.queue`
- `startup.*` would match `startup.created` AND `startup.rejected`
- `startup.#` would match any sub-topic

**Queue** — A buffer that stores messages until a consumer (NotificationService) picks them up.
- `QueueBuilder.durable(...)` — the queue survives RabbitMQ restarts. Non-durable queues are deleted on restart, losing messages.

**Binding** — Connects a queue to an exchange with a routing key.
- "Bind `startup.created.queue` to `founderlink.exchange`, and route messages with key `startup.created` to this queue."

**Why constants?** (`public static final String EXCHANGE_NAME = "founderlink.exchange"`)
- These names must match EXACTLY between publisher (StartupService) and consumer (NotificationService).
- Using constants avoids typos.
- NotificationService uses `@Value` from its config file for the queue names (a different approach — both work).

---

## File 6: LoggingAspect.java (same pattern as AuthService)

Applied to all service classes in the `service` package. Logs every method invocation with timing information. See AuthService guide for detailed explanation.

---

## StartupFollower Entity

```java
@Entity
@Table(name = "startup_followers")
public class StartupFollower {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long startupId;
    private Long investorId;    // The user who followed (investor or co-founder)
    ...
}
```

This is a simple join table recording which user follows which startup. `startupFollowerRepository.existsByStartupIdAndInvestorId()` checks if someone is already following.

---

## Interview Q&A

**Q: What is CQRS and how is it implemented in StartupService?**

CQRS (Command Query Responsibility Segregation) is a pattern that separates read operations (queries) from write operations (commands). In StartupService, we have two interfaces: `StartupCommandService` (create, update, delete, approve, reject, follow) and `StartupQueryService` (get by id, get all, search, isFollowing). Both are implemented by `StartupServiceImpl`. The controller has separate service instances for commands and queries. This makes the code more readable and provides a foundation for more advanced CQRS where commands and queries use different data stores.

**Q: How does the startup search with multiple optional filters work?**

We use JPA Specifications — a Criteria API-based approach for building dynamic queries. `StartupSpecification` provides factory methods that return individual `Specification<Startup>` predicates (e.g., `hasIndustry(String industry)` returns a spec that adds a `WHERE industry = ?` clause). In the service, we start with `Specification.where(isApproved())` and progressively `and()` additional specs only if the corresponding parameter is non-null. The final composed specification is passed to `startupRepository.findAll(spec, pageable)` which generates the complete SQL query dynamically.

**Q: What is the startup approval workflow?**

When a founder creates a startup, `isApproved` is set to `false` by default. The startup is not visible in public listings. An admin calls `PUT /startups/{id}/approve` (protected by `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`). The admin endpoint is at `/startups/admin/all` to see all startups (approved and unapproved). Once approved, `isApproved` is set to `true` and the startup appears in `GET /startups`. If rejected, `isRejected` is set to `true` and a `startup.rejected` event is published to notify the founder.

**Q: Why is BigDecimal used for fundingGoal instead of double?**

`double` and `float` are binary floating-point types — they cannot represent decimal fractions exactly. `0.1 + 0.2` in `double` gives `0.30000000000000004`, not `0.3`. For financial calculations, this is unacceptable. `BigDecimal` is a decimal arithmetic type with exact representation. `precision = 15, scale = 2` means we can store values up to `9,999,999,999,999.99` with exactly 2 decimal places — perfect for funding amounts in rupees.

**Q: How does Redis caching work in StartupService?**

`@Cacheable(value = "startups", key = "#id")` checks Redis first on `getStartupById`. If the key `startups::42` exists in Redis, the cached value is returned without a DB query. When a startup is updated/approved/rejected, `@CacheEvict(value = "startups", key = "#id")` removes the stale cached entry. On the next request, fresh data is fetched from PostgreSQL and re-cached. `@Caching` groups multiple evictions — for example, approving startup 42 evicts both `startups::42` AND the entire `startupsByFounder` cache since the founder's startup list is also affected.
