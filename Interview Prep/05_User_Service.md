# User Service — Interview Preparation Guide

---

## What does User Service do?

The User Service manages **user profiles** — the extended information about a user beyond just their login credentials. While AuthService handles `name`, `email`, `password`, and `role`, UserService manages richer profile data like `bio`, `skills`, `experience`, and `portfolioLinks`.

**Separation of Concerns:**
- `AuthService` → identity: "who are you?" (authentication)
- `UserService` → profile: "tell me about yourself" (profile data for the platform)

A user must first register via AuthService, then optionally fill in their profile via UserService.

---

## Key Features

1. **Create Profile** — A user creates their profile (bio, skills, experience)
2. **Get Profile** — Fetch a user's profile by userId
3. **Update Profile** — Edit profile data
4. **Admin: Get All Profiles** — Paginated list of all profiles
5. **Search by Skill** — Find users with a specific skill (e.g., "React")
6. **Batch Profile Fetch** — Get profiles for multiple userIds at once
7. **Redis Caching** — Profile data is cached in Redis to avoid repeated DB hits

---

## Architecture Overview

```
[Client] → [API Gateway] → [JwtAuthenticationFilter reads X-User-Id, X-User-Roles]
                               ↓
                       [UserProfileController]
                               ↓
              [UserProfileCommandService] [UserProfileQueryService]
                               ↓
                    [UserProfileServiceImpl]
                               ↓
              [Redis Cache] ←→ [UserProfileRepository] → [PostgreSQL]
```

---

## File 1: JwtAuthenticationFilter.java

This is a **servlet filter** (not a Gateway filter) that runs on every incoming HTTP request to UserService.

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {

        // Path 1: Trust headers from API Gateway
        String userIdHeader = request.getHeader("X-User-Id");
        String rolesHeader = request.getHeader("X-User-Roles");

        if (userIdHeader != null && rolesHeader != null) {
            Long userId = Long.parseLong(userIdHeader);
            List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                    .map(role -> new SimpleGrantedAuthority(role.trim()))
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId.toString(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }

        // Path 2: Fallback — validate JWT directly
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                // Extract userId and roles from JWT
                // Set authentication in SecurityContext
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

**`extends OncePerRequestFilter`**
- Spring MVC filters can be called multiple times per request (for forwards/includes). `OncePerRequestFilter` guarantees the filter runs exactly ONCE per request.
- This is the standard base class for authentication filters in Spring Boot.

**Two Authentication Paths:**

**Path 1: X-User-Id and X-User-Roles headers (normal flow)**
- When a request comes through the API Gateway, the gateway validates the JWT and injects `X-User-Id: 42` and `X-User-Roles: ROLE_INVESTOR` headers.
- UserService's filter reads these headers and creates a `UsernamePasswordAuthenticationToken`.
- This token is stored in `SecurityContextHolder` — Spring Security's thread-local store for the current user's authentication.
- The service method can then do `authentication.getName()` to get `"42"` (the userId as a string).

**Path 2: Direct JWT (fallback for testing or Feign calls)**
- Some requests may come directly (e.g., during local testing without gateway, or from Feign clients).
- In this case, the filter validates the JWT directly using `JwtUtil`.

**`UsernamePasswordAuthenticationToken(userId.toString(), null, authorities)`**
- `principal` = `userId.toString()` → This is what `authentication.getName()` returns in controllers.
- `credentials` = `null` → No password needed for JWT-based auth.
- `authorities` = list of `SimpleGrantedAuthority` objects → used by `@PreAuthorize` to check roles.

**`SecurityContextHolder.getContext().setAuthentication(authentication)`**
- Stores the authentication object in a thread-local variable.
- Spring Security reads from here when evaluating `@PreAuthorize` expressions.

---

## File 2: UserProfileController.java

```java
@RestController
@RequestMapping("/users")
public class UserProfileController {

    @PostMapping("/profile")
    public ResponseEntity<UserProfileResponse> createProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileRequest request) {

        Long userId = Long.parseLong(authentication.getName());
        UserProfileResponse response = userProfileCommandService.createProfile(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
```

**`Authentication authentication`**
- Spring injects the current user's `Authentication` object (set by `JwtAuthenticationFilter`).
- `authentication.getName()` returns the `principal` — which we set to `userId.toString()` in the filter.
- `Long.parseLong(authentication.getName())` converts "42" back to `42L`.

```java
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfileByUserId(@PathVariable("id") Long userId) {
        try {
            UserProfileResponse response = userProfileQueryService.getProfileByUserId(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(UserProfileResponse.builder().userId(userId).build());
        }
    }
```

This endpoint returns an empty profile shell instead of a 404 if the user hasn't created their profile yet. The frontend uses this to render a blank profile form.

```java
    @PutMapping("/{id}")
    @PreAuthorize("authentication.name == #id.toString() or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserProfileRequest request) {
```

**`@PreAuthorize("authentication.name == #id.toString() or hasAuthority('ROLE_ADMIN')")`**
- This is Spring Security's method-level authorization using Spring Expression Language (SpEL).
- `authentication.name` = the userId of the currently logged-in user (set as principal).
- `#id` = the `id` path variable from the URL.
- The expression allows the request ONLY if:
  - The logged-in user's ID matches the ID in the URL (you can only update YOUR OWN profile), OR
  - The user has the `ROLE_ADMIN` role.
- If the condition is false → Spring Security throws `AccessDeniedException` → returns HTTP 403 Forbidden.

```java
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<UserProfileResponse>> getAllProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<UserProfileResponse> profiles = userProfileQueryService.getAllProfiles(pageable);
        return ResponseEntity.ok(profiles);
    }
```

**`@PreAuthorize("hasAuthority('ROLE_ADMIN')")`** — Only admins can see all profiles.

**`Pageable pageable = PageRequest.of(page, size)`**
- Creates a pagination object. `page=0, size=10` means "give me the first 10 records."
- Spring Data JPA's `findAll(Pageable)` uses this to add `LIMIT 10 OFFSET 0` to the SQL query.
- Returns a `Page<T>` which includes the data plus pagination metadata (total pages, total elements, current page).

```java
    @GetMapping("/profiles/batch")
    @PreAuthorize("hasAuthority('ROLE_FOUNDER') or hasAuthority('ROLE_COFOUNDER')")
    public ResponseEntity<List<UserProfileResponse>> getProfilesBatch(
            @RequestParam String userIds,
            @RequestParam(required = false) String skill) {

        List<Long> ids = Arrays.stream(userIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        List<UserProfileResponse> results = userProfileQueryService.getProfilesByUserIds(ids, skill);
        return ResponseEntity.ok(results);
    }
```

This endpoint is called by the frontend when a founder searches for co-founders:
1. Frontend calls AuthService to get all users with `ROLE_COFOUNDER`.
2. AuthService returns a list of userIds.
3. Frontend calls this endpoint with `userIds=1,2,3&skill=React`.
4. UserService returns profiles for those specific users, optionally filtered by skill.

---

## File 3: UserProfileServiceImpl.java

```java
@Service
@Transactional
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {
```

### createProfile:

```java
@Caching(evict = {
    @CacheEvict(value = "userProfiles", key = "#userId"),
    @CacheEvict(value = "userSkillSearch", allEntries = true)
})
public UserProfileResponse createProfile(Long userId, UserProfileRequest request) {
    if (userProfileRepository.existsByUserId(userId)) {
        throw new DuplicateResourceException("Profile already exists for user ID: " + userId);
    }
    if (userProfileRepository.existsByEmail(request.getEmail())) {
        throw new DuplicateResourceException("Email already in use: " + request.getEmail());
    }
    UserProfile profile = UserProfile.builder()...build();
    UserProfile saved = userProfileRepository.save(profile);
    return userProfileMapper.toResponse(saved);
}
```

**`@Caching(evict = { @CacheEvict(...), @CacheEvict(...) })`**
- `@Caching` allows combining multiple cache annotations.
- `@CacheEvict(value = "userProfiles", key = "#userId")` — removes the cached profile for this user (in case it was cached as "not found").
- `@CacheEvict(value = "userSkillSearch", allEntries = true)` — clears ALL skill search cache entries because a new profile might affect search results.

### getProfileByUserId:

```java
@Transactional(readOnly = true)
@Cacheable(value = "userProfiles", key = "#userId")
public UserProfileResponse getProfileByUserId(Long userId) {
    log.info("Fetching user profile from DB: userId={}", userId);
    UserProfile profile = userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user ID: " + userId));
    return userProfileMapper.toResponse(profile);
}
```

**`@Cacheable(value = "userProfiles", key = "#userId")`**
- On first call, Spring checks Redis for key `userProfiles::42`. If NOT there, executes the method and stores the result.
- On subsequent calls with the same userId, Spring returns the cached value WITHOUT executing the method body or hitting the database.
- `#userId` — Spring Expression Language syntax to use the method parameter as the cache key.

**`@Transactional(readOnly = true)`**
- Marks the transaction as read-only. The database can use optimizations (e.g., skip undo log generation in PostgreSQL).
- Spring also skips dirty checking (tracking entity changes) since this is a read.

**`Optional.orElseThrow()`**
- `findByUserId()` returns `Optional<UserProfile>`. If the value is not present, throw `ResourceNotFoundException` which is caught by `GlobalExceptionHandler` and returned as HTTP 404.

---

## File 4: UserProfile.java (Entity)

```java
@Entity
@Table(name = "user_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;   // Foreign key reference to auth_service.users.id

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String bio;           // Long text — stored as PostgreSQL TEXT type

    private String skills;        // Comma-separated or JSON string
    private String experience;

    @Column(columnDefinition = "TEXT")
    private String portfolioLinks;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

**`@Column(columnDefinition = "TEXT")`**
- In PostgreSQL, `VARCHAR` has a maximum length limit. `TEXT` is unlimited (up to 1GB).
- Used for `bio` and `portfolioLinks` which could be long.

**`@CreationTimestamp`** (Hibernate annotation)
- Hibernate automatically sets this field to the current timestamp when the entity is first inserted.
- `@Column(updatable = false)` — once set, this value cannot be changed by updates.

**`@UpdateTimestamp`** (Hibernate annotation)
- Hibernate automatically updates this field to the current timestamp every time the entity is saved/updated.
- Together, these timestamps give you a full audit trail of when a profile was created and last modified.

**`Long userId`** — Note: There's NO `@ManyToOne` join to `UserEntity`. This is intentional. UserService is a separate microservice from AuthService. They have separate databases. The `userId` is just a number that corresponds to the ID in AuthService's users table — the relationship is maintained at the application level, not at the database level. This is the **shared database vs separate database** pattern — in FounderLink, each service has its own database.

---

## Redis Caching Architecture

```
RedisConfig.java → configures RedisConnectionFactory and CacheManager
   ↓
@EnableCaching (in UserServiceApplication)
   ↓
@Cacheable, @CacheEvict annotations trigger
   ↓
Spring's RedisCacheManager stores/retrieves data from Redis
```

**Why Redis?**
- User profiles are read far more often than they're written.
- Instead of hitting PostgreSQL on every profile fetch, we store the result in Redis (in-memory, microsecond access).
- The cache is invalidated (cleared) whenever a profile is updated or created.

---

## Interview Q&A

**Q: What is the difference between UserService and AuthService in FounderLink?**

AuthService handles authentication — it stores credentials (email, hashed password, role) and issues JWT tokens. It answers "who are you?" UserService handles profiles — it stores extended user information like bio, skills, experience, and portfolio links. It answers "tell me about yourself." This separation follows the Single Responsibility Principle: authentication logic is isolated from profile management. If we wanted to add LinkedIn integration or profile picture upload, we'd modify UserService without touching the authentication logic.

**Q: How does `@PreAuthorize` work in UserService?**

`@PreAuthorize` is a Spring Security annotation that evaluates a SpEL (Spring Expression Language) expression before executing the method. For `updateProfile`, the expression is `authentication.name == #id.toString() or hasAuthority('ROLE_ADMIN')`. Spring Security intercepts the method call, evaluates the expression, and only allows execution if it returns `true`. `authentication.name` comes from `SecurityContextHolder` — it was set by `JwtAuthenticationFilter` when it read the `X-User-Id` header and created the authentication object with the userId as the principal.

**Q: What is Redis caching and why do we use it?**

Redis is an in-memory data store used as a cache. When `getProfileByUserId(42)` is called, `@Cacheable` first checks if key `userProfiles::42` exists in Redis. If yes, it returns the cached result immediately without any database query. If no, it runs the method, fetches from PostgreSQL, and stores the result in Redis. This dramatically reduces database load and response latency for frequently accessed data. The cache is invalidated (`@CacheEvict`) when a profile is created or updated to ensure consistency.

**Q: What is pagination and how does Spring Data support it?**

Pagination is returning data in pages (e.g., 10 records per page) instead of returning all records at once. This is critical when the table has thousands of rows — returning all at once would be slow and memory-intensive. Spring Data JPA supports pagination through the `Pageable` interface. `PageRequest.of(0, 10)` creates a page request for page 0 (first page) with 10 items. Spring Data automatically generates `SELECT ... LIMIT 10 OFFSET 0` SQL. The returned `Page<T>` object contains the data plus metadata: total pages, total elements, current page number.

**Q: Why doesn't UserProfile have a foreign key relationship to UserEntity from AuthService?**

Because they're in DIFFERENT services with DIFFERENT databases. AuthService owns the `auth_service` PostgreSQL schema and UserService owns the `user_service` schema. Cross-database foreign key constraints don't exist. Instead, UserProfile has a `userId` column that stores the userId from AuthService — it's an application-level reference, not a database-level foreign key. This is the **database-per-service** pattern in microservices. The tradeoff is that you lose automatic referential integrity and must handle consistency at the application level (e.g., if a user is deleted in AuthService, UserService still has the profile — cleanup would need to be handled via events).