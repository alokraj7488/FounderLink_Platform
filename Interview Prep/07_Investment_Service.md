# Investment Service — Interview Preparation Guide

---

## What does Investment Service do?

The Investment Service manages **investment proposals** between investors and startup founders.

**Flow:**
1. An **investor** sees a startup and wants to invest → creates an investment request
2. The **founder** of that startup reviews the investment → approves or rejects it
3. Events are published to RabbitMQ → NotificationService informs both parties

Key challenge: An investment involves data from TWO services — the investor's identity (from AuthService) and the startup details (from StartupService). InvestmentService calls StartupService via **Feign Client** (inter-service HTTP call) to validate that the startup exists and to get the founder's ID.

---

## Key Features

1. **Create Investment** — Investor proposes an investment amount for a startup
2. **Approve Investment** — Founder approves a pending investment
3. **Reject Investment** — Founder rejects a pending investment
4. **List by Startup** — Get all investments for a startup
5. **List by Investor** — Get all investments by an investor
6. **Circuit Breaker** — If StartupService is down, InvestmentService handles it gracefully
7. **Redis Caching** — Investment lists are cached
8. **RabbitMQ Events** — Publishes investment.created and investment.approved events

---

## Architecture: Feign Client + Circuit Breaker

```
[Investor: POST /investments]
    ↓
[InvestmentController]
    ↓
[InvestmentService]
    ├── FeignClient → StartupService: GET /startups/{id}
    │       ↓ if StartupService DOWN
    │       CircuitBreaker → FallbackFactory → throws ServiceUnavailableException
    │
    ├── Save Investment to DB
    └── Publish investment.created event → RabbitMQ → NotificationService
```

---

## File 1: InvestmentServiceApplication.java

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableCaching
public class InvestmentServiceApplication { ... }
```

**`@EnableFeignClients`**
- Activates Spring Cloud OpenFeign. Scans for interfaces annotated with `@FeignClient` and creates HTTP client implementations for them.
- Without this, `@FeignClient` interfaces would be ignored.

**`@EnableCaching`**
- Activates Spring's caching abstraction. Without this, `@Cacheable` and `@CacheEvict` annotations do nothing.

---

## File 2: StartupClient.java (Feign Client)

```java
@FeignClient(
    name = "startup-service",
    configuration = FeignClientConfig.class,
    fallbackFactory = StartupClientFallbackFactory.class
)
public interface StartupClient {

    @GetMapping("/startups/{id}")
    StartupDTO getStartupById(@PathVariable("id") Long id);
}
```

**`@FeignClient(name = "startup-service")`**
- This interface becomes an HTTP client that calls `startup-service`.
- `name = "startup-service"` — Feign looks up `startup-service` in Eureka to get its address.
- Spring generates a proxy implementation at runtime: when you call `startupClient.getStartupById(42)`, it makes an HTTP GET request to `http://startup-service/startups/42`.

**`@GetMapping("/startups/{id}")`**
- The method maps to `GET /startups/{id}` on the startup-service.
- Feign uses this to build the HTTP request. The returned JSON is deserialized into `StartupDTO`.

**`configuration = FeignClientConfig.class`**
- Custom configuration for this Feign client (e.g., timeout settings, interceptors to forward the JWT header).

**`fallbackFactory = StartupClientFallbackFactory.class`**
- If the startup-service call fails (network error, timeout, startup-service is down), Feign uses this factory to create a fallback implementation instead of throwing an exception.

---

## File 3: StartupClientFallbackFactory.java

```java
@Component
@Slf4j
public class StartupClientFallbackFactory implements FallbackFactory<StartupClient> {

    @Override
    public StartupClient create(Throwable cause) {
        log.error("[CIRCUIT BREAKER] startup-service unavailable: {}", cause.getMessage());
        return id -> {
            throw new ServiceUnavailableException(
                    "Startup service is currently unavailable. Please try again later.");
        };
    }
}
```

**`FallbackFactory<StartupClient>`**
- `FallbackFactory` is preferred over a simple `fallback` because it gives you access to the `Throwable cause` — the actual exception that caused the failure.
- This allows you to log the specific error or return different fallback responses depending on the error type.

**`create(Throwable cause)`**
- This method is called every time the circuit is open (service is down).
- It returns a lambda that implements `StartupClient.getStartupById(id)`.
- The lambda throws `ServiceUnavailableException` — which the controller/global exception handler catches and returns HTTP 503 Service Unavailable to the client.

**Why use a fallback instead of letting the exception propagate?**
- Without a fallback, if StartupService is temporarily down, ALL investment creation requests would fail with an ugly 500 error.
- With a fallback, we return a meaningful 503 error: "Startup service is currently unavailable. Please try again later." — much better user experience.

---

## File 4: InvestmentService.java (Core Business Logic)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentService implements InvestmentCommandService, InvestmentQueryService {

    private final InvestmentRepository investmentRepository;
    private final StartupClient startupClient;
    private final EventPublisher eventPublisher;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final InvestmentMapper investmentMapper;

    private StartupDTO fetchStartup(Long startupId) {
        return circuitBreakerFactory.create("startup-service").run(
                () -> startupClient.getStartupById(startupId),
                throwable -> {
                    log.error("[CIRCUIT BREAKER] startup-service unavailable: {}", throwable.getMessage());
                    throw new ServiceUnavailableException(
                            "Startup service is currently unavailable. Please try again later.");
                }
        );
    }
```

**`CircuitBreakerFactory`**
- Spring Cloud CircuitBreaker abstraction. The implementation (Resilience4j, Hystrix) can be swapped out.
- `circuitBreakerFactory.create("startup-service")` — creates a circuit breaker named "startup-service".
- `.run(supplier, fallback)`:
  - `supplier` — the code to try: `() -> startupClient.getStartupById(startupId)` — calls StartupService via Feign.
  - `fallback` — what to do if the supplier throws: log and throw `ServiceUnavailableException`.

**Circuit Breaker States:**
```
CLOSED (normal) → supplier executes normally
    ↓ (too many failures)
OPEN (tripped) → supplier is NOT called, fallback executes immediately
    ↓ (after timeout)
HALF-OPEN → try one request; if success → CLOSED; if failure → OPEN again
```

This prevents a cascading failure: if StartupService is down, we don't keep sending failing requests. Instead, we immediately fail fast and spare StartupService the load.

### createInvestment:

```java
@Override
@Transactional
@Caching(evict = {
    @CacheEvict(value = "investmentsByStartup", key = "#request.startupId"),
    @CacheEvict(value = "investmentsByInvestor", key = "#investorId")
})
public InvestmentResponse createInvestment(InvestmentRequest request, Long investorId) {
    // Step 1: Validate startup exists
    StartupDTO startup = fetchStartup(request.getStartupId());
    if (startup == null) {
        throw new ResourceNotFoundException("Startup not found with id: " + request.getStartupId());
    }

    // Step 2: Create and save investment
    Investment investment = Investment.builder()
            .startupId(request.getStartupId())
            .investorId(investorId)
            .amount(request.getAmount())
            .status(InvestmentStatus.PENDING)
            .build();
    investment = investmentRepository.save(investment);

    // Step 3: Publish event with founder's ID (from StartupDTO)
    eventPublisher.publishInvestmentCreated(InvestmentCreatedEvent.builder()
            .investmentId(investment.getId())
            .startupId(investment.getStartupId())
            .investorId(investment.getInvestorId())
            .founderId(startup.getFounderId())  // From StartupService response!
            .amount(investment.getAmount())
            .build());

    return investmentMapper.toResponse(investment);
}
```

**Why do we need `startup.getFounderId()`?**
- When an investment is created, we want to notify the founder ("You received an investment proposal").
- InvestmentService doesn't store the founder's ID — it calls StartupService to get it.
- This is why the Feign client call is essential.

### approveInvestment:

```java
@Override
@Transactional
public InvestmentResponse approveInvestment(Long investmentId, Long founderId) {
    Investment investment = investmentRepository.findById(investmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Investment not found"));

    // Guard: can only approve PENDING investments
    if (investment.getStatus() != InvestmentStatus.PENDING) {
        throw new BadRequestException("Investment is not in PENDING status");
    }

    // Authorization: only the startup's founder can approve
    StartupDTO startup = fetchStartup(investment.getStartupId());
    if (!startup.getFounderId().equals(founderId)) {
        throw new UnauthorizedException("Only the startup founder can approve investments");
    }

    investment.setStatus(InvestmentStatus.APPROVED);
    investment = investmentRepository.save(investment);

    // Notify investor
    eventPublisher.publishInvestmentApproved(InvestmentApprovedEvent.builder()
            .investmentId(investment.getId())
            .investorId(investment.getInvestorId())
            .amount(investment.getAmount())
            .build());

    return investmentMapper.toResponse(investment);
}
```

**Multi-step authorization check:**
1. Investment must exist → otherwise 404.
2. Investment must be PENDING → can't approve an already approved/rejected investment.
3. The caller (founderId) must be the founder of the startup associated with this investment → call StartupService to verify.

---

## File 5: Investment.java (Entity)

```java
@Entity
@Table(name = "investments")
public class Investment {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = InvestmentStatus.PENDING;
        }
    }
}
```

**`@PrePersist`** — Runs before the entity is first saved. Sets `createdAt` to current time and defaults `status` to `PENDING` if not already set.

**InvestmentStatus enum:**
```java
public enum InvestmentStatus {
    PENDING,   // Awaiting founder decision
    APPROVED,  // Founder accepted
    REJECTED   // Founder declined
}
```

---

## File 6: InvestmentController.java

```java
@RestController
@RequestMapping("/investments")
@RequiredArgsConstructor
public class InvestmentController {

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_INVESTOR')")
    public ResponseEntity<InvestmentResponse> createInvestment(
            @Valid @RequestBody InvestmentRequest request,
            Authentication authentication) {
        Long investorId = Long.parseLong(authentication.getName());
        InvestmentResponse response = investmentCommandService.createInvestment(request, investorId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ROLE_FOUNDER') or hasAuthority('ROLE_COFOUNDER')")
    public ResponseEntity<InvestmentResponse> approveInvestment(
            @PathVariable Long id, Authentication authentication) {
        Long founderId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(investmentCommandService.approveInvestment(id, founderId));
    }
```

Only investors can create investments. Only founders or co-founders can approve/reject.

---

## Interview Q&A

**Q: What is a Feign Client and how does it work?**

Feign is a declarative HTTP client for microservices. Instead of manually building `RestTemplate` or `HttpClient` requests, you define an interface with Spring MVC annotations (`@GetMapping`, `@PathVariable`), and Feign generates the actual HTTP client implementation at runtime. `@FeignClient(name = "startup-service")` tells Feign to look up `startup-service` in Eureka, get its address, and make HTTP calls to it. This makes inter-service communication look like a simple method call, hiding all the HTTP boilerplate.

**Q: What is a Circuit Breaker and why do we need it?**

A Circuit Breaker prevents cascading failures in distributed systems. If InvestmentService calls StartupService and StartupService is down, without a circuit breaker, every investment request would hang for the timeout duration before failing. If 100 users try simultaneously, 100 threads are blocked — InvestmentService itself becomes unresponsive. A circuit breaker monitors failure rates. When failures exceed a threshold, it "opens" the circuit and immediately returns a fallback response without even attempting the call. After a timeout, it tries again. This pattern protects the entire system from a single service failure.

**Q: What is the difference between a fallback and a fallbackFactory in Feign?**

A `fallback` is a simple class that implements the Feign interface with fallback behavior (e.g., return empty lists or null). A `fallbackFactory` is a factory that takes the `Throwable` (the actual exception that caused the failure) as input and creates the fallback. The `fallbackFactory` approach is more powerful because you can inspect WHY the call failed (network timeout vs HTTP 500) and provide different fallback behavior or logging for each case.

**Q: How does InvestmentService know which founder to notify when an investment is created?**

InvestmentService stores only `startupId` and `investorId` — not the `founderId`. To get the founder's ID, InvestmentService calls `startupClient.getStartupById(startupId)` which returns a `StartupDTO` containing the `founderId`. This FounderId is then included in the `InvestmentCreatedEvent` published to RabbitMQ. NotificationService listens to this event and uses the `founderId` to send the notification to the correct founder.

**Q: What does the InvestmentStatus state machine look like?**

All investments start as `PENDING`. From PENDING, the founder can either set it to `APPROVED` or `REJECTED`. Once approved or rejected, the status cannot change (we guard against this: `if (status != PENDING) throw BadRequestException`). APPROVED means the founder accepts the investment proposal. REJECTED means they decline. When approved, an `InvestmentApprovedEvent` is published, which eventually triggers the payment flow in PaymentService.
