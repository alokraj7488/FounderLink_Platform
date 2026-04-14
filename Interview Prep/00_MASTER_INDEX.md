# FounderLink — Interview Preparation Master Index

---

## The 11 Microservices

| # | Service | Port | File | Key Tech |
|---|---------|------|------|---------|
| 1 | Eureka Server | 8761 | `01_Eureka_Server.md` | Service Discovery, @EnableEurekaServer |
| 2 | Config Server | 8888 | `02_Config_Server.md` | Centralized Config, @EnableConfigServer, Git backend |
| 3 | API Gateway | 8080 | `03_API_Gateway.md` | Spring Cloud Gateway, WebFlux, JWT filter, CORS |
| 4 | Auth Service | 8081 | `04_Auth_Service.md` | JWT, BCrypt, Spring Security, RabbitMQ publisher |
| 5 | User Service | 8082 | `05_User_Service.md` | Redis caching, @PreAuthorize SpEL, Pagination |
| 6 | Startup Service | 8083 | `06_Startup_Service.md` | CQRS, JPA Specifications, Redis, RabbitMQ |
| 7 | Investment Service | 8084 | `07_Investment_Service.md` | Feign Client, Circuit Breaker, RabbitMQ |
| 8 | Team Service | 8085 | `08_Team_Service.md` | Feign, Saga-like invite workflow, RabbitMQ |
| 9 | Messaging Service | 8086 | `09_Messaging_Service.md` | WebSocket, STOMP, SimpMessagingTemplate |
| 10 | Notification Service | 8087 | `10_Notification_Service.md` | @RabbitListener, WebSocket push, event consumer |
| 11 | Payment Service | 8090 | `11_Payment_Service.md` | Razorpay, Saga Pattern, @PrePersist/@PreUpdate |

---

## Quick Reference: Key Annotations

| Annotation | Service | What it does |
|---|---|---|
| `@EnableEurekaServer` | Eureka | Turns app into service registry |
| `@EnableConfigServer` | Config | Turns app into config server |
| `@EnableDiscoveryClient` | All (except Eureka) | Registers with Eureka |
| `@EnableFeignClients` | Investment, Team | Enables Feign HTTP clients |
| `@EnableMethodSecurity` | Startup, User, Investment, Team, Notification | Enables @PreAuthorize |
| `@EnableCaching` | User, Startup, Investment, Team | Enables @Cacheable, @CacheEvict |
| `@EnableAsync` | Auth | Enables @Async (async email) |
| `@EnableWebSocketMessageBroker` | Messaging, Notification | Enables WebSocket/STOMP |
| `@SpringBootApplication` | All | Auto-config + component scan + config |
| `@RestController` | All controllers | Returns JSON responses |
| `@Service` | All services | Business logic layer bean |
| `@Repository` | Implicit in JPA | Data access layer |
| `@Component` | Filters, Aspects | Generic Spring bean |
| `@Configuration` | All configs | Spring config class with @Bean methods |
| `@Bean` | All configs | Declares a Spring-managed bean |

---

## Quick Reference: Security Annotations

| Annotation | Example | Meaning |
|---|---|---|
| `@PreAuthorize` | `@PreAuthorize("hasAuthority('ROLE_FOUNDER')")` | Role check before method runs |
| `@Valid` | `@Valid @RequestBody` | Trigger bean validation |
| `@NotBlank` | `@NotBlank String name` | Must not be empty/null |
| `@Email` | `@Email String email` | Must be valid email format |
| `@Size(min=6)` | `@Size(min=6) String password` | Minimum length |

---

## Quick Reference: JPA/Entity Annotations

| Annotation | What it does |
|---|---|
| `@Entity` | Maps class to DB table |
| `@Table(name="...")` | Specifies the table name |
| `@Id` | Primary key |
| `@GeneratedValue(IDENTITY)` | Auto-increment ID |
| `@Column(nullable=false, unique=true)` | DB constraint |
| `@Enumerated(EnumType.STRING)` | Store enum as string in DB |
| `@ManyToMany` | Many-to-many relationship |
| `@JoinTable` | Defines the join table |
| `@CreationTimestamp` | Hibernate auto-sets on insert |
| `@UpdateTimestamp` | Hibernate auto-sets on update |
| `@PrePersist` | JPA callback before first insert |
| `@PreUpdate` | JPA callback before update |
| `@Builder.Default` | Lombok builder default value |

---

## Quick Reference: Caching Annotations

| Annotation | What it does |
|---|---|
| `@Cacheable(value="cache", key="#id")` | Read from cache, write if miss |
| `@CacheEvict(value="cache", key="#id")` | Remove specific key from cache |
| `@CacheEvict(value="cache", allEntries=true)` | Clear entire cache |
| `@Caching(evict={...})` | Multiple cache operations |

---

## Quick Reference: RabbitMQ Annotations

| Annotation | What it does |
|---|---|
| `@RabbitListener(queues="...")` | Consume messages from queue |

---

## Common Interview Questions with Quick Answers

### Architecture Questions

**Q: What is the startup order for FounderLink?**
```
1. Config Server (8888) — all others need config
2. Eureka Server (8761) — all others need discovery
3. Business services (Auth, User, Startup, etc.) — need both
4. API Gateway (8080) — routes to all services
```

**Q: How does a request flow from frontend to database?**
```
React → API Gateway → JwtAuthFilter (validates JWT, injects X-User-Id/X-User-Roles)
      → Microservice → JwtAuthenticationFilter (reads headers, sets SecurityContext)
      → Controller → @PreAuthorize check → Service → Repository → PostgreSQL
```

**Q: How do services communicate?**
- **Synchronous (real-time):** Feign Client (HTTP) — Investment/Team → Startup
- **Asynchronous (event-driven):** RabbitMQ — Auth/Startup/Investment/Team/Payment → Notification
- **Real-time push to browser:** WebSocket (Messaging, Notification)

**Q: How is data isolated between services?**
Each service has its own PostgreSQL database schema. No cross-service database joins. Services communicate only through HTTP APIs or message events.

---

## The Full Data Flow: "Investor invests in a startup"

```
1. [Frontend] POST /auth/login → AuthService → returns JWT
2. [Frontend] GET /startups → StartupService → lists approved startups
3. [Frontend] POST /investments → API Gateway → JWT validated
   → InvestmentService
   → Feign: GET /startups/{id} → StartupService (get founderId)
   → Save Investment (PENDING)
   → Publish investment.created → RabbitMQ
   → NotificationService: notify founder "New investment received"
4. [Founder] PUT /investments/{id}/approve
   → InvestmentService: status=APPROVED
   → Publish investment.approved → NotificationService: notify investor
5. [Investor] POST /api/payments/create-order
   → PaymentService: create Razorpay order
   → Saga: ORDER_CREATED
6. [Investor pays via Razorpay UI]
   POST /api/payments/verify
   → Verify signature → status=AWAITING_APPROVAL
   → Saga: AWAITING_APPROVAL
   → Event: payment.pending → Founder notified
7. [Founder] PUT /api/payments/{id}/accept
   → PaymentService: status=SUCCESS
   → Saga: COMPLETED
   → Event: payment.success → Both notified + emails sent
```

---

## Patterns Used in FounderLink

| Pattern | Where used | What it solves |
|---|---|---|
| **Service Discovery** | Eureka | Dynamic service location |
| **API Gateway** | api-gateway | Single entry point, auth, CORS |
| **Centralized Config** | Config Server | One config file per service |
| **CQRS** | Startup, User, Investment, Team | Separate read/write interfaces |
| **Circuit Breaker** | Investment, Team | Graceful degradation when dependency is down |
| **Event-Driven** | RabbitMQ (all services) | Loose coupling, async notifications |
| **Saga** | Payment | Distributed transaction management |
| **JWT Stateless Auth** | API Gateway + all services | Scalable, sessionless auth |
| **Redis Cache** | User, Startup, Investment, Team | Performance for read-heavy data |
| **AOP Logging** | All services (LoggingAspect) | Cross-cutting concern without code modification |
| **WebSocket** | Messaging, Notification | Real-time communication |
| **Specification Pattern** | Startup search | Dynamic query building |
