# Auth Service — Interview Preparation Guide

---

## What does Auth Service do?

The Auth Service is the **authentication and authorization hub** of FounderLink. It is responsible for:
1. **User Registration** — creating new accounts with hashed passwords and roles
2. **User Login** — validating credentials and issuing JWT tokens
3. **Token Refresh** — generating a new access token when the old one expires
4. **User Lookup** — providing user data (name, email, role) to other services

It is the ONLY service that talks to the `users` and `roles` tables in the database. All other services trust the JWT token that AuthService issues.

---

## Architecture Overview

```
[Client POST /auth/register]
     ↓
[AuthController]
     ↓
[IAuthService → AuthService]
     ├── Validate email uniqueness
     ├── Encode password (BCrypt)
     ├── Save to DB (UserEntity)
     ├── Send welcome email (async)
     └── Publish user.registered event to RabbitMQ
          ↓
     [NotificationService receives event]

[Client POST /auth/login]
     ↓
[AuthController → AuthService.login()]
     ├── Find user by email
     ├── Verify password with BCrypt
     ├── Generate Access Token (JWT, 1 hour)
     ├── Generate Refresh Token (JWT, 7 days)
     └── Return AuthResponse
```

---

## Dependencies (pom.xml — key ones)

| Dependency | What it does |
|---|---|
| `spring-boot-starter-web` | REST API endpoints (Spring MVC) |
| `spring-boot-starter-security` | Spring Security framework for authentication |
| `spring-boot-starter-data-jpa` | JPA/Hibernate for database operations |
| `postgresql` | PostgreSQL JDBC driver |
| `io.jsonwebtoken:jjwt-api/impl/jackson` | JJWT library for creating and validating JWT tokens |
| `spring-amqp` + `spring-rabbit` | RabbitMQ messaging to publish events |
| `spring-boot-starter-mail` | JavaMail for sending welcome emails |
| `spring-boot-starter-validation` | Bean validation (`@NotBlank`, `@Email`, `@Size`) |
| `lombok` | Auto-generates getters, setters, constructors, builder |
| `springdoc-openapi` | Auto-generates Swagger UI documentation |

---

## File 1: AuthServiceApplication.java

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

**`@EnableDiscoveryClient`**
- Registers this service with Eureka Server so the API Gateway can discover it.
- On startup, AuthService sends a registration request to `http://eureka-server:8761/eureka/apps/AUTH-SERVICE`.
- Sends heartbeats every 30 seconds to stay registered.

**`@EnableAsync`**
- Enables Spring's asynchronous method execution capability.
- Any method annotated with `@Async` will run in a separate thread pool instead of blocking the current thread.
- In AuthService, `WelcomeEmailService.sendWelcome()` is likely `@Async` — sending an email shouldn't block the registration response.

---

## File 2: SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                    .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

**`@Configuration`**
- Marks this as a Spring configuration class — Spring will call its `@Bean` methods to register beans in the application context.

**`@EnableWebSecurity`**
- Activates Spring Security's web security support. Without this, Spring Security would use default settings (which require authentication for everything).

**`SecurityFilterChain` bean**
- This is the new (Spring Boot 3.x) way to configure HTTP security. Previously you'd extend `WebSecurityConfigurerAdapter` (now deprecated).
- It defines a chain of security rules applied to incoming HTTP requests.

**`.csrf(AbstractHttpConfigurer::disable)`**
- CSRF (Cross-Site Request Forgery) protection is disabled.
- **Why?** CSRF protection uses session cookies. Since we use JWT (stateless, no cookies), CSRF protection is unnecessary and would break our API.
- `AbstractHttpConfigurer::disable` is a method reference — equivalent to `csrf -> csrf.disable()`.

**`SessionCreationPolicy.STATELESS`**
- Tells Spring Security NEVER to create an HTTP session. Every request must be self-contained with a JWT.
- In traditional web apps, Spring Security stores authentication in a session. In microservices with JWT, we don't use sessions.

**`.requestMatchers("/auth/**").permitAll()`**
- `/auth/register`, `/auth/login`, `/auth/refresh` — anyone can call these without authentication.
- `.permitAll()` means "no authentication required."

**`.anyRequest().authenticated()`**
- Every other endpoint requires an authenticated user (valid JWT).

**`BCryptPasswordEncoder`**
- BCrypt is a strong password hashing algorithm. It's **one-way** — you can't reverse-engineer the original password from the hash.
- BCrypt includes a **salt** (random data) in the hash, so even if two users have the same password, their hashes will be different.
- When a user logs in, `passwordEncoder.matches(rawPassword, storedHash)` runs BCrypt on the raw password and compares.

**`AuthenticationManager`**
- This bean is needed for programmatic authentication (e.g., in login flows where you verify credentials manually).
- `config.getAuthenticationManager()` returns the default authentication manager configured by Spring Security.

---

## File 3: JwtUtil.java

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;  // e.g., 3600000 ms = 1 hour

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration; // e.g., 604800000 ms = 7 days

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
```

**`@Component`**
- Marks this as a Spring-managed bean. Spring creates one instance (singleton) and injects it wherever needed.

**`@Value("${jwt.secret}")`**
- Reads the `jwt.secret` property from the configuration (which comes from Config Server → environment variable `JWT_SECRET`).
- This is a BASE64-encoded string that serves as the HMAC signing key.

**`Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))`**
- Decodes the BASE64 string into bytes.
- Creates an HMAC-SHA key from those bytes.
- HMAC-SHA256 is the algorithm used to sign JWTs in this project (HS256).

### Token Generation:

```java
public String generateAccessToken(Long userId, String email, String role) {
    return buildToken(Map.of(
            "userId", userId,
            "email", email,
            "roles", List.of(role)
    ), email, accessTokenExpiration);
}

private String buildToken(Map<String, Object> claims, String subject, long expiration) {
    return Jwts.builder()
            .claims(claims)           // Custom claims: userId, email, roles
            .subject(subject)          // Standard claim: "sub" = email
            .issuedAt(new Date())      // Standard claim: "iat" = current time
            .expiration(new Date(System.currentTimeMillis() + expiration))  // "exp"
            .signWith(getSigningKey()) // Sign with HMAC-SHA256 key
            .compact();                // Build the JWT string
}
```

**JWT Structure:** A JWT has three parts separated by dots: `header.payload.signature`
- **Header:** `{"alg": "HS256", "typ": "JWT"}` — encoded as BASE64
- **Payload (claims):** `{"userId": 42, "email": "alice@example.com", "roles": ["ROLE_FOUNDER"], "sub": "alice@example.com", "iat": 1713000000, "exp": 1713003600}` — encoded as BASE64
- **Signature:** `HMAC_SHA256(header + "." + payload, secret)` — prevents tampering

**`claims`** — Custom data embedded in the token. The API Gateway extracts `userId` and `roles` from these claims without calling the database.

**`subject`** — The standard JWT `sub` claim. We use the email as the subject.

**`issuedAt`** — The `iat` claim: when the token was issued.

**`expiration`** — The `exp` claim: when the token expires.

### Token Validation:

```java
public boolean validateToken(String token) {
    try {
        extractAllClaims(token);
        return true;
    } catch (Exception e) {
        return false;
    }
}

private Claims extractAllClaims(String token) {
    return Jwts.parser()
            .verifyWith(getSigningKey())  // Check signature integrity
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

**How validation works:**
1. `Jwts.parser().verifyWith(key)` — set the key to use for signature verification
2. `.parseSignedClaims(token)` — parse the JWT string
   - Checks the signature: if the token was tampered with, the signature won't match → throws exception
   - Checks the `exp` claim: if the token is expired → throws `ExpiredJwtException`
3. `.getPayload()` — returns the `Claims` object with all data
4. If any exception is thrown, `validateToken()` returns `false`

---

## File 4: AuthController.java

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final UserRepository userRepository;
    private final AuthMapper authMapper;
```

**`@RestController`**
- Combines `@Controller` (marks as a web controller) and `@ResponseBody` (all return values are serialized to JSON directly).
- Without `@ResponseBody`, Spring would try to find a view template for the return value.

**`@RequestMapping("/auth")`**
- All endpoints in this controller are prefixed with `/auth`.
- So `@PostMapping("/register")` becomes `POST /auth/register`.

**`@RequiredArgsConstructor`** (Lombok)
- Generates a constructor with ALL `final` fields as parameters.
- Spring uses this constructor to inject dependencies (constructor injection — the recommended approach over field injection).
- `final IAuthService authService` → Spring injects the `AuthService` bean.

### Endpoints:

```java
@PostMapping("/register")
public ResponseEntity<ApiResponse<RegisterResponse>> register(
        @Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("User registered successfully", response));
}
```

**`@PostMapping("/register")`** — Handles HTTP POST requests to `/auth/register`.

**`@Valid`** — Triggers Bean Validation. Spring validates the `RegisterRequest` object against its constraints (`@NotBlank`, `@Email`, `@Size(min=6)`). If validation fails, Spring throws `MethodArgumentNotValidException` which is caught by `GlobalExceptionHandler`.

**`@RequestBody RegisterRequest request`** — Spring deserializes the JSON request body into a `RegisterRequest` Java object.

**`ResponseEntity<ApiResponse<RegisterResponse>>`** — Returns an HTTP response with:
- Status code: `201 Created`
- Body: `ApiResponse` wrapper containing `RegisterResponse`

**`ApiResponse.success("message", data)`** — A generic wrapper object: `{success: true, message: "...", data: {...}}`. Standardizes all API responses.

```java
@GetMapping("/users/{id}")
public ResponseEntity<UserSummaryDto> getUserById(@PathVariable Long id) {
    return userRepository.findById(id)
            .map(u -> ResponseEntity.ok(authMapper.toUserSummaryDto(u)))
            .orElse(ResponseEntity.notFound().build());
}
```

**`@PathVariable Long id`** — Extracts the `{id}` from the URL path and binds it to the `id` parameter. e.g., `/auth/users/42` → `id = 42`.

**`.map(...).orElse(ResponseEntity.notFound().build())`** — `findById` returns `Optional<UserEntity>`. If present, map to DTO and return 200. If empty, return 404.

---

## File 5: AuthService.java (Business Logic)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements IAuthService {
```

**`@Service`**
- Marks this as a Spring service bean (a specialization of `@Component` for business logic layer).
- Spring creates one instance and registers it in the application context.

**`@Slf4j`** (Lombok)
- Generates a static `log` field: `private static final Logger log = LoggerFactory.getLogger(AuthService.class);`
- Allows you to use `log.info(...)`, `log.error(...)` etc.

### Register Method:

```java
@Transactional
public RegisterResponse register(RegisterRequest request) {
    // Guard 1: No admin self-registration
    if ("ROLE_ADMIN".equalsIgnoreCase(request.getRole())) {
        throw new CustomException("Admin accounts cannot be self-registered", HttpStatus.FORBIDDEN);
    }

    // Guard 2: Email uniqueness
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new CustomException("Email already registered", HttpStatus.CONFLICT);
    }

    // Find role from DB
    RoleEntity role = roleRepository.findByName(request.getRole())
            .orElseThrow(() -> new CustomException("Invalid role: " + request.getRole(), HttpStatus.BAD_REQUEST));

    // Build and save user
    UserEntity user = UserEntity.builder()
            .name(request.getName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))  // BCrypt hash
            .roles(Set.of(role))
            .build();

    UserEntity savedUser = userRepository.save(user);

    // Send welcome email (async — doesn't block response)
    welcomeEmailService.sendWelcome(savedUser.getEmail(), savedUser.getName(), role.getName());

    // Publish event to RabbitMQ
    try {
        rabbitTemplate.convertAndSend(exchange, "user.registered",
                UserRegisteredEvent.builder()
                        .userId(savedUser.getId())
                        .name(savedUser.getName())
                        .email(savedUser.getEmail())
                        .role(role.getName())
                        .build());
    } catch (Exception e) {
        log.warn("Failed to publish user.registered event: {}", e.getMessage());
        // Non-critical: registration succeeds even if messaging fails
    }

    return RegisterResponse.builder()...build();
}
```

**`@Transactional`**
- Wraps the entire method in a database transaction.
- If `userRepository.save(user)` succeeds but then an exception occurs later in the method (before return), Spring rolls back the database changes — the user is NOT saved.
- This ensures data consistency: either the whole operation succeeds or nothing is saved.

**`passwordEncoder.encode(request.getPassword())`**
- BCrypt hashes the raw password. The hash looks like: `$2a$10$...` (60 characters).
- The number after `$2a$` is the "strength factor" (10 by default). Higher = slower hashing = more secure but slower.

**`rabbitTemplate.convertAndSend(exchange, "user.registered", event)`**
- Sends the `UserRegisteredEvent` object as a JSON message to RabbitMQ.
- `exchange` = `"founderlink.exchange"` (TopicExchange)
- Routing key = `"user.registered"`
- NotificationService is listening on a queue bound to this routing key — it will receive this event and create a welcome notification.
- **Non-critical:** Wrapped in try-catch because if RabbitMQ is unavailable, registration should still succeed. The event loss is acceptable in this case.

---

## File 6: UserEntity.java

```java
@Entity
@Table(name = "users")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

**`@Entity`** — Marks this class as a JPA entity. JPA will create/manage the corresponding database table.

**`@Table(name = "users")`** — Maps this entity to the `users` table in PostgreSQL.

**`@Id`** — Marks `id` as the primary key.

**`@GeneratedValue(strategy = GenerationType.IDENTITY)`** — The database auto-generates the ID (auto-increment in PostgreSQL using sequences).

**`@Column(nullable = false, unique = true)`** — This column cannot be null in the database AND must be unique (a database-level constraint, not just application-level).

**`@ManyToMany(fetch = FetchType.EAGER)`** — A user can have multiple roles, and a role can belong to multiple users.
- `EAGER` means: when you load a `UserEntity`, immediately also load all its roles from the DB.
- Without EAGER, accessing `user.getRoles()` outside a transaction would throw a `LazyInitializationException`.
- **Why EAGER here?** Because in the login flow, we immediately need the user's role to generate the JWT. EAGER avoids an extra database call.

**`@JoinTable`** — Defines the join table `user_roles` with columns `user_id` and `role_id`.

**`@PrePersist`** — A JPA lifecycle callback. This method runs AUTOMATICALLY just BEFORE an entity is first inserted into the database. Used to set `createdAt` automatically without the developer needing to set it manually.

**`@Data`** (Lombok) — Generates getters, setters, `toString()`, `equals()`, `hashCode()` for all fields.

**`@Builder`** (Lombok) — Enables the Builder pattern: `UserEntity.builder().name("Alice").email("alice@example.com").build()`.

**`@NoArgsConstructor`** (Lombok) — Generates a no-argument constructor (required by JPA).

**`@AllArgsConstructor`** (Lombok) — Generates a constructor with all fields (required by `@Builder` to work properly).

---

## File 7: GlobalExceptionHandler.java

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(400, errors));
    }
}
```

**`@RestControllerAdvice`**
- A global exception handler that applies to ALL controllers in the application.
- It's a combination of `@ControllerAdvice` (catch exceptions from all controllers) and `@ResponseBody` (return JSON, not a view).
- Without this, unhandled exceptions would return a generic Spring error response (`500 Internal Server Error` with HTML stacktrace).

**`@ExceptionHandler(CustomException.class)`**
- Whenever any controller throws a `CustomException`, this method handles it.
- Returns the appropriate HTTP status code (403, 409, etc.) and a clean JSON error message.

**`MethodArgumentNotValidException`**
- Thrown when `@Valid` validation fails on a request body.
- We collect all field-level errors and join them into a readable string.
- e.g., `"email: must be a well-formed email address, password: size must be between 6 and 2147483647"`

---

## File 8: RabbitMQConfig.java

```java
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Bean
    public TopicExchange founderLinkExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

**`TopicExchange`**
- AuthService only **publishes** events (user.registered), it doesn't consume any.
- So we only need the exchange declaration — no queues or bindings here.
- `TopicExchange` allows routing based on pattern-matching routing keys (e.g., `user.*` matches `user.registered`, `user.updated`).

**`Jackson2JsonMessageConverter`**
- Configures RabbitMQ to serialize/deserialize Java objects as JSON.
- Without this, RabbitMQ would use Java serialization (binary format) — less compatible and harder to debug.

---

## File 9: LoggingAspect.java (AOP)

```java
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("execution(* com.capgemini.authservice.service..*(..))")
    public Object logServiceMethods(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();
        log.info("[START] {}", method);
        try {
            Object result = pjp.proceed();
            log.info("[END] {} | {}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[ERROR] {} | {}ms | {}: {}",
                    method, System.currentTimeMillis() - start,
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;  // Re-throw so the original exception handling still works
        }
    }
}
```

**AOP (Aspect-Oriented Programming)**
- AOP allows you to add behavior (logging, security, transactions) to existing code WITHOUT modifying the original code.
- **Aspect:** A class that contains cross-cutting concerns (like logging).
- **Pointcut:** The expression that matches which methods to intercept.
- **Advice:** What to do when the pointcut matches (before, after, around).

**`@Aspect`** — Marks this class as an AOP aspect. Spring will use it to intercept matched method calls.

**`@Component`** — Registers it as a Spring bean so Spring can apply it.

**`@Around("execution(* com.capgemini.authservice.service..*(..))")`**
- `@Around` — runs BEFORE and AFTER the method (wraps it completely).
- Pointcut expression breakdown:
  - `execution(...)` — match method executions
  - `*` — any return type
  - `com.capgemini.authservice.service..*` — any class in the `service` package or sub-packages (`..` = any depth)
  - `(..)` — any number/type of parameters
- This intercepts ALL methods in ALL service classes.

**`ProceedingJoinPoint pjp`** — Represents the intercepted method call. `pjp.proceed()` actually executes the original method.

**`pjp.getSignature().toShortString()`** — Gets a short description of the method being called, e.g., `AuthService.register(RegisterRequest)`.

**Flow:** `[START] AuthService.register(..)` → method runs → `[END] AuthService.register(..) | 45ms` OR `[ERROR] AuthService.register(..) | 12ms | CustomException: Email already registered`.

---

## Interview Q&A

**Q: How does JWT authentication work in AuthService?**

When a user logs in, AuthService validates their credentials against the database. If valid, it generates two JWTs using `JwtUtil`:
1. **Access Token** (short-lived, e.g., 1 hour) — contains userId, email, and roles. Used for API calls.
2. **Refresh Token** (long-lived, e.g., 7 days) — contains userId and email only. Used to get a new access token when the access token expires.
Both tokens are signed with an HMAC-SHA256 secret key. The API Gateway validates the access token on every request by re-computing the signature. If the token was tampered with or expired, the signature won't match and the request is rejected.

**Q: What is BCrypt and why do we use it for passwords?**

BCrypt is an adaptive hashing algorithm designed for password storage. Unlike MD5 or SHA-256 (which are fast), BCrypt is intentionally slow — making brute-force attacks impractical. It generates a unique salt for each password, so identical passwords produce different hashes. BCrypt's "work factor" (strength) can be increased over time as hardware gets faster. We use `BCryptPasswordEncoder` from Spring Security which handles salt generation and matching automatically.

**Q: What is `@Transactional` and when do you use it?**

`@Transactional` wraps a method in a database transaction. All database operations within the method either all succeed (commit) or all fail (rollback). Use it on service methods that perform multiple database operations that must be atomic. For example, in `register()`, if we save the user but then publishing the RabbitMQ event threw an exception we wanted to handle transactionally — without `@Transactional`, partial writes could corrupt data. In read-only methods, use `@Transactional(readOnly = true)` which allows database optimizations.

**Q: What is AOP (Aspect-Oriented Programming) and how is it used here?**

AOP allows adding cross-cutting concerns — functionality that spans multiple parts of the codebase (logging, security, transactions) — without modifying the business logic code. In AuthService, the `LoggingAspect` automatically intercepts every service method call, logs when it starts, how long it took, and if it threw an error. The service classes don't have any logging code — the aspect adds it transparently. Spring implements AOP using proxies: when you call `authService.login()`, Spring actually calls the proxy's method, which runs the aspect code around the real method.

**Q: How does FounderLink handle the case where a user tries to register with an existing email?**

In `AuthService.register()`, before saving, we call `userRepository.existsByEmail(request.getEmail())`. If true, we throw `CustomException("Email already registered", HttpStatus.CONFLICT)`. The `GlobalExceptionHandler` catches this and returns HTTP 409 with a JSON error body. The `@Column(unique = true)` constraint on the email field also enforces this at the database level as a secondary safety net.